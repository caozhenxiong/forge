package devflow.agent.supervisor;

import devflow.agent.context.ContextAccessProfile;
import devflow.agent.context.ProjectedContext;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.ReviewResult;

/**
 * 负责 supervisor 阶段流转决策 prompt。
 *
 * <p>把阶段决策 prompt 抽出来后，
 * `SupervisorPromptAssembler` 只保留门面职责，不再同时维护两套长模板。
 */
final class SupervisorDecisionPromptBuilder {

    private final SupervisorArtifactRenderer artifactRenderer;
    private final SupervisorPromptTextSupport textSupport = new SupervisorPromptTextSupport();

    SupervisorDecisionPromptBuilder(SupervisorArtifactRenderer artifactRenderer) {
        this.artifactRenderer = artifactRenderer;
    }

    String systemPrompt() {
        return """
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
                """;
    }

    String userPrompt(
            RunRecord runRecord,
            StageType currentStage,
            StageType nextStage,
            GatePolicy gatePolicy,
            StageExecution currentExecution,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            ProjectedContext projectedContext,
            SupervisorDecision fallback
    ) {
        DocumentLanguage language = DocumentLanguage.detect(runRecord.goal(), runRecord.constraints());
        return """
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

                Supervisor Context Slice：
                %s

                默认保守决策参考：
                - action: %s
                - targetStage: %s
                - mode: %s
                - reason: %s
                - deliveryPolicy: %s
                """.formatted(
                runRecord.goal(),
                textSupport.blank(runRecord.constraints()),
                currentStage,
                PlaceholderValues.orMachineNull(nextStage == null ? null : nextStage.toString()),
                gatePolicy,
                currentExecution == null ? 0 : currentExecution.attempt(),
                reviewResult.decision(),
                reviewResult.fixMode(),
                textSupport.blank(reviewResult.summary()),
                textSupport.blank(reviewResult.changeRequest()),
                textSupport.blank(reviewResult.evidence()),
                textSupport.blank(reviewResult.actionItems()),
                repeatedIssue,
                projectedContext.toMarkdown(ContextAccessProfile.SUPERVISOR, language),
                fallback.action(),
                PlaceholderValues.orMachineNull(fallback.targetStage() == null ? null : fallback.targetStage().toString()),
                fallback.mode(),
                textSupport.blank(fallback.reason()),
                artifactRenderer.renderPolicy(fallback.deliveryPolicy())
        );
    }
}
