package devflow.agent.supervisor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
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

    public SupervisorAgent(LlmProvider llmProvider, FileArtifactStore artifactStore, ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.artifactStore = artifactStore;
        this.objectMapper = objectMapper;
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
        SupervisorDecision fallback = fallbackDecision(currentStage, nextStage, gatePolicy, reviewResult, repeatedIssue);
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
                            8. 对复杂前端/网页/游戏任务，优先选择更小粒度的推进方式；focus/constraints 要尽量把当前轮次收缩成“先可运行骨架，再渐进填充”。
                            9. 不要鼓励单轮写完整个产品；如果当前问题复杂，优先约束为 1-2 个小目标。
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

                            当前 artifact 摘要：
                            %s

                            最近 review history 摘要：
                            %s

                            repair brief 摘要：
                            %s

                            默认保守决策参考：
                            - action: %s
                            - targetStage: %s
                            - mode: %s
                            - reason: %s
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
                            shrink(readCurrentArtifact(projectPath, runRecord, currentStage)),
                            shrink(readReviewHistory(projectPath, runRecord, currentStage)),
                            shrink(readRepairBrief(projectPath, runRecord)),
                            fallback.action(),
                            fallback.targetStage() == null ? "null" : fallback.targetStage(),
                            fallback.mode(),
                            fallback.reason()
                    ),
                    Map.of("num_predict", 220),
                    ModelRole.SUPERVISOR
            );
            DecisionPayload payload = objectMapper.readValue(extractJsonObject(response), DecisionPayload.class);
            return sanitizeDecision(payload, currentStage, nextStage, gatePolicy, reviewResult, repeatedIssue, fallback);
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
                renderList(decision.constraints())
        );
    }

    private SupervisorDecision sanitizeDecision(
            DecisionPayload payload,
            StageType currentStage,
            StageType nextStage,
            GatePolicy gatePolicy,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision fallback
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
                payload.humanRequired() != null && payload.humanRequired()
        );
    }

    private SupervisorDecision fallbackDecision(
            StageType currentStage,
            StageType nextStage,
            GatePolicy gatePolicy,
            ReviewResult reviewResult,
            boolean repeatedIssue
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
                        true
                );
            }
            return new SupervisorDecision(
                    SupervisorAction.ADVANCE_STAGE,
                    nextStage,
                    FixMode.NONE,
                    "当前阶段已通过，推进到下一阶段。",
                    List.of("进入 " + nextStage + " 并生成新产物"),
                    List.of(),
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
        return new SupervisorDecision(
                action,
                retryStage,
                reviewResult.fixMode(),
                reason,
                List.of(blank(reviewResult.summary()), blank(reviewResult.changeRequest())).stream()
                        .filter(item -> !item.isBlank())
                        .toList(),
                List.of("不要偏离当前 review 提示的主问题"),
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

    private record DecisionPayload(
            @JsonProperty("action") String action,
            @JsonProperty("targetStage") String targetStage,
            @JsonProperty("mode") String mode,
            @JsonProperty("reason") String reason,
            @JsonProperty("focus") List<String> focus,
            @JsonProperty("constraints") List<String> constraints,
            @JsonProperty("humanRequired") Boolean humanRequired
    ) {
    }
}
