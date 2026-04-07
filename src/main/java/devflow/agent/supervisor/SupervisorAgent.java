package devflow.agent.supervisor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ProjectedContext;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SupervisorAgent {

    private final LlmProvider llmProvider;
    private final FileArtifactStore artifactStore;
    private final ObjectMapper objectMapper;
    private final ContextProjector contextProjector;

    public SupervisorAgent(
            LlmProvider llmProvider,
            FileArtifactStore artifactStore,
            ObjectMapper objectMapper,
            ContextProjector contextProjector
    ) {
        this.llmProvider = llmProvider;
        this.artifactStore = artifactStore;
        this.objectMapper = objectMapper;
        this.contextProjector = contextProjector;
    }

    public SupervisorDecision decide(
            Path projectPath,
            RunRecord runRecord,
            StageType currentStage,
            ReviewResult reviewResult,
            boolean repeatedIssue
    ) {
        StageExecution currentExecution = runRecord.stageStates().get(currentStage);
        StageType nextStage = nextStage(currentStage);
        GatePolicy gatePolicy = runRecord.config().gatePolicies().getOrDefault(currentStage, GatePolicy.AGENT_ONLY);
        ProjectedContext projectedContext = contextProjector.project(projectPath, runRecord, currentStage);
        SupervisorDecision fallback = fallbackDecision(currentStage, nextStage, gatePolicy, reviewResult, repeatedIssue, projectedContext);
        if (llmProvider == null) {
            return fallback;
        }

        try {
            String response = llmProvider.generate(
                    """
                            你是 SupervisorAgent，负责决定 Forge 的下一步流程动作。
                            你必须只返回 JSON，格式如下：
                            {
                              "action": "ADVANCE_STAGE|REQUEST_HUMAN_REVIEW|RETRY_STAGE|ROUTE_TO_REPAIR|ROLLBACK_STAGE|COMPLETE_RUN|FAIL_RUN",
                              "targetStage": "ANALYSIS|PRD|DESIGN|IMPLEMENTATION|CODE_REVIEW|TEST|null",
                              "mode": "NONE|PATCH|REWORK",
                              "reason": "一句话说明",
                              "focus": ["本轮必须优先处理的问题"],
                              "constraints": ["本轮额外约束"],
                              "requiredEvidence": ["下一轮必须补出的证据"],
                              "deliveryPolicy": {
                                "mode": "SKELETON|INCREMENTAL|PATCH|REWORK",
                                "maxFiles": 2,
                                "maxSymbols": 4,
                                "preferPreciseEditing": true,
                                "forceBacklogSplit": false,
                                "requireVerificationBeforeReview": true
                              },
                              "humanRequired": false
                            }

                            规则：
                            1. 你只负责流程决策，不直接写代码。
                            2. REVIEW 通过后，优先在 ADVANCE_STAGE、REQUEST_HUMAN_REVIEW、COMPLETE_RUN 中选择。
                            3. REVIEW 未通过时，优先在 RETRY_STAGE、ROUTE_TO_REPAIR、ROLLBACK_STAGE、FAIL_RUN 中选择。
                            4. ROUTE_TO_REPAIR 只在重复问题已明确、适合定点修补时使用。
                            5. ROLLBACK_STAGE 只在根因明显属于上游文档或设计时使用。
                            6. 不要凭空跳过阶段，不要选择无效 targetStage。
                            7. reason/focus/constraints 必须简洁、可执行。
                            8. deliveryPolicy 必须体现“本轮最多改多少文件、是否强制继续拆小、是否优先走精确 patch”。
                            9. 不要鼓励单轮写完整个产品；如果当前问题复杂，优先约束为 1-2 个小目标。
                            10. requiredEvidence 只写对下一轮收敛真正必要的证据。
                            """,
                    """
                            任务目标：
                            %s

                            约束：
                            %s

                            当前阶段：
                            %s

                            下一阶段：
                            %s

                            当前阶段 gate：
                            %s

                            当前阶段 attempt：
                            %d

                            review 结论：
                            - decision: %s
                            - fixMode: %s
                            - summary: %s
                            - changeRequest: %s
                            - evidence: %s
                            - actionItems: %s

                            是否已识别为重复问题：
                            %s

                            Projected Context / 当前阶段摘要：
                            %s

                            上游约束摘要：
                            %s

                            最近历史摘要：
                            %s

                            失败摘要：
                            %s

                            repair brief 摘要：
                            %s

                            当前工作集摘要：
                            %s

                            默认保守决策参考：
                            - action: %s
                            - targetStage: %s
                            - mode: %s
                            - reason: %s
                            - deliveryPolicy: %s
                            """.formatted(
                            runRecord.goal(),
                            blank(runRecord.constraints()),
                            currentStage,
                            nextStage == null ? "null" : nextStage,
                            gatePolicy,
                            currentExecution == null ? 0 : currentExecution.attempt(),
                            reviewResult.decision(),
                            reviewResult.fixMode(),
                            blank(reviewResult.summary()),
                            blank(reviewResult.changeRequest()),
                            blank(reviewResult.evidence()),
                            blank(reviewResult.actionItems()),
                            repeatedIssue,
                            projectedContext.currentStageSummary(),
                            projectedContext.upstreamContractSummary(),
                            projectedContext.recentHistorySummary(),
                            projectedContext.failureSummary(),
                            projectedContext.repairSummary(),
                            projectedContext.workingSetSummary(),
                            fallback.action(),
                            fallback.targetStage() == null ? "null" : fallback.targetStage(),
                            fallback.mode(),
                            fallback.reason(),
                            renderPolicy(fallback.deliveryPolicy())
                    ),
                    Map.of("num_predict", 220),
                    ModelRole.SUPERVISOR
            );
            DecisionPayload payload = objectMapper.readValue(extractJsonObject(response), DecisionPayload.class);
            return sanitizeDecision(payload, currentStage, nextStage, gatePolicy, reviewResult, repeatedIssue, fallback, projectedContext);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public String renderDecisionArtifact(
            StageType currentStage,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision decision
    ) {
        return """
                # Supervisor 决策

                - currentStage: %s
                - reviewDecision: %s
                - reviewFixMode: %s
                - repeatedIssue: %s
                - action: %s
                - targetStage: %s
                - mode: %s
                - humanRequired: %s
                - reason: %s

                ## Focus

                %s

                ## Constraints

                %s

                ## Required Evidence

                %s

                ## Delivery Policy

                - mode: %s
                - maxFiles: %s
                - maxSymbols: %s
                - preferPreciseEditing: %s
                - forceBacklogSplit: %s
                - requireVerificationBeforeReview: %s
                """.formatted(
                currentStage,
                reviewResult.decision(),
                reviewResult.fixMode(),
                repeatedIssue,
                decision.action(),
                decision.targetStage() == null ? "null" : decision.targetStage(),
                decision.mode(),
                decision.humanRequired(),
                blank(decision.reason()),
                renderList(decision.focus()),
                renderList(decision.constraints()),
                renderList(decision.requiredEvidence()),
                decision.deliveryPolicy().mode(),
                decision.deliveryPolicy().maxFiles(),
                decision.deliveryPolicy().maxSymbols(),
                decision.deliveryPolicy().preferPreciseEditing(),
                decision.deliveryPolicy().forceBacklogSplit(),
                decision.deliveryPolicy().requireVerificationBeforeReview()
        );
    }

    private SupervisorDecision sanitizeDecision(
            DecisionPayload payload,
            StageType currentStage,
            StageType nextStage,
            GatePolicy gatePolicy,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision fallback,
            ProjectedContext projectedContext
    ) {
        if (payload == null || payload.action() == null || payload.action().isBlank()) {
            return fallback;
        }
        SupervisorAction action;
        try {
            action = SupervisorAction.valueOf(payload.action().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
        StageType targetStage = parseStage(payload.targetStage());
        FixMode mode = parseFixMode(payload.mode(), reviewResult.fixMode());

        if (reviewResult.decision() == ReviewDecision.APPROVED) {
            if (!(action == SupervisorAction.ADVANCE_STAGE
                    || action == SupervisorAction.REQUEST_HUMAN_REVIEW
                    || action == SupervisorAction.COMPLETE_RUN)) {
                return fallback;
            }
            if (action == SupervisorAction.REQUEST_HUMAN_REVIEW && gatePolicy != GatePolicy.AGENT_PLUS_HUMAN) {
                return fallback;
            }
            if (action == SupervisorAction.ADVANCE_STAGE && nextStage == null) {
                return fallback;
            }
            if (action == SupervisorAction.COMPLETE_RUN && nextStage != null) {
                return fallback;
            }
            if (action == SupervisorAction.ADVANCE_STAGE) {
                targetStage = nextStage;
            } else if (action == SupervisorAction.REQUEST_HUMAN_REVIEW) {
                targetStage = currentStage;
            } else {
                targetStage = currentStage;
            }
        } else {
            if (!(action == SupervisorAction.RETRY_STAGE
                    || action == SupervisorAction.ROUTE_TO_REPAIR
                    || action == SupervisorAction.ROLLBACK_STAGE
                    || action == SupervisorAction.FAIL_RUN
                    || action == SupervisorAction.REQUEST_HUMAN_REVIEW)) {
                return fallback;
            }
            if (action == SupervisorAction.ROUTE_TO_REPAIR) {
                targetStage = StageType.IMPLEMENTATION;
                if (!repeatedIssue) {
                    return fallback;
                }
            }
            if (action == SupervisorAction.RETRY_STAGE && targetStage == null) {
                targetStage = fallback.targetStage();
            }
            if (action == SupervisorAction.ROLLBACK_STAGE) {
                if (targetStage == null || targetStage.ordinal() >= currentStage.ordinal()) {
                    return fallback;
                }
            }
            if (action == SupervisorAction.REQUEST_HUMAN_REVIEW) {
                if (gatePolicy != GatePolicy.AGENT_PLUS_HUMAN) {
                    return fallback;
                }
                targetStage = currentStage;
            }
        }

        return new SupervisorDecision(
                action,
                targetStage,
                mode,
                blank(payload.reason()),
                normalizeList(payload.focus()),
                normalizeList(payload.constraints()),
                normalizeList(payload.requiredEvidence()),
                sanitizePolicy(payload.deliveryPolicy(), currentStage, reviewResult, repeatedIssue, projectedContext, fallback.deliveryPolicy()),
                payload.humanRequired() != null && payload.humanRequired()
        );
    }

    private SupervisorDecision fallbackDecision(
            StageType currentStage,
            StageType nextStage,
            GatePolicy gatePolicy,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            ProjectedContext projectedContext
    ) {
        if (reviewResult.decision() == ReviewDecision.APPROVED) {
            if (nextStage == null) {
                return new SupervisorDecision(
                        SupervisorAction.COMPLETE_RUN,
                        currentStage,
                        FixMode.NONE,
                        "最后阶段已通过，结束 run。",
                        List.of("归档最终产物"),
                        List.of(),
                        List.of("最终阶段通过证据"),
                        DeliveryPolicy.balanced("NONE"),
                        false
                );
            }
            if (gatePolicy == GatePolicy.AGENT_PLUS_HUMAN) {
                return new SupervisorDecision(
                        SupervisorAction.REQUEST_HUMAN_REVIEW,
                        currentStage,
                        FixMode.NONE,
                        "当前阶段需要人工 gate，先阻塞等待人工批准。",
                        List.of("等待人工确认当前阶段产物"),
                        List.of("保持当前 artifact 不变"),
                        List.of("人工审批结果"),
                        DeliveryPolicy.balanced("NONE"),
                        true
                );
            }
            DeliveryPolicy deliveryPolicy = nextStage == StageType.IMPLEMENTATION
                    ? initialImplementationPolicy(projectedContext)
                    : DeliveryPolicy.balanced("INCREMENTAL");
            return new SupervisorDecision(
                    SupervisorAction.ADVANCE_STAGE,
                    nextStage,
                    FixMode.NONE,
                    "当前阶段已通过，推进到下一阶段。",
                    List.of("进入 " + nextStage + " 并生成新产物"),
                    List.of(),
                    List.of("进入下一阶段所需的基础 artifact"),
                    deliveryPolicy,
                    false
            );
        }

        StageType retryStage = switch (currentStage) {
            case ANALYSIS, PRD, DESIGN -> currentStage;
            case IMPLEMENTATION, CODE_REVIEW, TEST -> StageType.IMPLEMENTATION;
        };
        SupervisorAction action = repeatedIssue && retryStage == StageType.IMPLEMENTATION
                ? SupervisorAction.ROUTE_TO_REPAIR
                : SupervisorAction.RETRY_STAGE;
        String reason = repeatedIssue
                ? "检测到重复问题，优先进入 repair 路径做定点修补。"
                : "当前问题仍可收敛，先按既定回退路径继续修订。";
        DeliveryPolicy deliveryPolicy = switch (reviewResult.fixMode()) {
            case PATCH -> DeliveryPolicy.patchSafe();
            case REWORK -> DeliveryPolicy.reworkSafe();
            case NONE -> DeliveryPolicy.balanced("INCREMENTAL");
        };
        return new SupervisorDecision(
                action,
                retryStage,
                reviewResult.fixMode(),
                reason,
                List.of(blank(reviewResult.summary()), blank(reviewResult.changeRequest())).stream()
                        .filter(item -> !item.isBlank())
                        .toList(),
                List.of("不要偏离当前 review 提示的主问题"),
                buildRequiredEvidence(reviewResult),
                deliveryPolicy,
                false
        );
    }

    private String readCurrentArtifact(Path projectPath, RunRecord runRecord, StageType currentStage) {
        try {
            return artifactStore.readArtifact(projectPath, runRecord.runId(), currentStage);
        } catch (Exception exception) {
            return "";
        }
    }

    private String readReviewHistory(Path projectPath, RunRecord runRecord, StageType currentStage) {
        try {
            return artifactStore.readReviewHistory(projectPath, runRecord.runId(), currentStage);
        } catch (Exception exception) {
            return "";
        }
    }

    private String readRepairBrief(Path projectPath, RunRecord runRecord) {
        Path path = projectPath.resolve(".devflow").resolve("runs").resolve(runRecord.runId().toString()).resolve("repair_brief.md");
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            return "";
        }
    }

    private String renderList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- (empty)";
        }
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("- ").append(item);
        }
        return builder.isEmpty() ? "- (empty)" : builder.toString();
    }

    private String renderPolicy(DeliveryPolicy policy) {
        return """
                {mode=%s, maxFiles=%s, maxSymbols=%s, preferPreciseEditing=%s, forceBacklogSplit=%s, requireVerificationBeforeReview=%s}
                """.formatted(
                policy.mode(),
                policy.maxFiles(),
                policy.maxSymbols(),
                policy.preferPreciseEditing(),
                policy.forceBacklogSplit(),
                policy.requireVerificationBeforeReview()
        ).trim();
    }

    private List<String> normalizeList(List<String> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : rawItems) {
            if (item == null || item.isBlank()) {
                continue;
            }
            items.add(item.trim());
        }
        return items;
    }

    private StageType parseStage(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return StageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private FixMode parseFixMode(String value, FixMode defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue == null ? FixMode.NONE : defaultValue;
        }
        try {
            return FixMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return defaultValue == null ? FixMode.NONE : defaultValue;
        }
    }

    private StageType nextStage(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> StageType.PRD;
            case PRD -> StageType.DESIGN;
            case DESIGN -> StageType.IMPLEMENTATION;
            case IMPLEMENTATION -> StageType.CODE_REVIEW;
            case CODE_REVIEW -> StageType.TEST;
            case TEST -> null;
        };
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private String shrink(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 2200 ? normalized.substring(0, 2200) + " ...<truncated>" : normalized;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private List<String> buildRequiredEvidence(ReviewResult reviewResult) {
        List<String> items = new ArrayList<>();
        if (reviewResult.evidence() != null && !reviewResult.evidence().isBlank()) {
            items.add(reviewResult.evidence().trim());
        }
        if (reviewResult.actionItems() != null && !reviewResult.actionItems().isBlank()) {
            items.add(reviewResult.actionItems().trim());
        }
        if (items.isEmpty() && reviewResult.changeRequest() != null && !reviewResult.changeRequest().isBlank()) {
            items.add(reviewResult.changeRequest().trim());
        }
        return items;
    }

    private DeliveryPolicy initialImplementationPolicy(ProjectedContext projectedContext) {
        boolean existingWorkingSet = projectedContext.workingSetSummary() != null
                && !projectedContext.workingSetSummary().isBlank()
                && !projectedContext.workingSetSummary().contains("(workspace context unavailable)");
        return new DeliveryPolicy(
                existingWorkingSet ? "INCREMENTAL" : "SKELETON",
                2,
                existingWorkingSet ? 4 : 3,
                true,
                true,
                true
        );
    }

    private DeliveryPolicy sanitizePolicy(
            DeliveryPolicyPayload payload,
            StageType currentStage,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            ProjectedContext projectedContext,
            DeliveryPolicy fallback
    ) {
        if (payload == null) {
            return fallback;
        }
        String mode = blank(payload.mode()).toUpperCase(Locale.ROOT);
        if (!List.of("SKELETON", "INCREMENTAL", "PATCH", "REWORK", "NONE").contains(mode)) {
            mode = fallback.mode();
        }
        int maxFiles = payload.maxFiles() == null || payload.maxFiles() <= 0 || payload.maxFiles() > 3
                ? fallback.maxFiles()
                : payload.maxFiles();
        int maxSymbols = payload.maxSymbols() == null || payload.maxSymbols() <= 0 || payload.maxSymbols() > 12
                ? fallback.maxSymbols()
                : payload.maxSymbols();
        boolean preferPreciseEditing = payload.preferPreciseEditing() == null
                ? fallback.preferPreciseEditing()
                : payload.preferPreciseEditing();
        boolean forceBacklogSplit = payload.forceBacklogSplit() == null
                ? fallback.forceBacklogSplit()
                : payload.forceBacklogSplit();
        boolean requireVerificationBeforeReview = payload.requireVerificationBeforeReview() == null
                ? fallback.requireVerificationBeforeReview()
                : payload.requireVerificationBeforeReview();
        if (reviewResult.decision() != ReviewDecision.APPROVED && repeatedIssue) {
            forceBacklogSplit = true;
        }
        if (currentStage == StageType.DESIGN && "NONE".equals(mode)) {
            return initialImplementationPolicy(projectedContext);
        }
        return new DeliveryPolicy(mode, maxFiles, maxSymbols, preferPreciseEditing, forceBacklogSplit, requireVerificationBeforeReview);
    }

    private record DecisionPayload(
            @JsonProperty("action") String action,
            @JsonProperty("targetStage") String targetStage,
            @JsonProperty("mode") String mode,
            @JsonProperty("reason") String reason,
            @JsonProperty("focus") List<String> focus,
            @JsonProperty("constraints") List<String> constraints,
            @JsonProperty("requiredEvidence") List<String> requiredEvidence,
            @JsonProperty("deliveryPolicy") DeliveryPolicyPayload deliveryPolicy,
            @JsonProperty("humanRequired") Boolean humanRequired
    ) {
    }

    private record DeliveryPolicyPayload(
            @JsonProperty("mode") String mode,
            @JsonProperty("maxFiles") Integer maxFiles,
            @JsonProperty("maxSymbols") Integer maxSymbols,
            @JsonProperty("preferPreciseEditing") Boolean preferPreciseEditing,
            @JsonProperty("forceBacklogSplit") Boolean forceBacklogSplit,
            @JsonProperty("requireVerificationBeforeReview") Boolean requireVerificationBeforeReview
    ) {
    }
}
