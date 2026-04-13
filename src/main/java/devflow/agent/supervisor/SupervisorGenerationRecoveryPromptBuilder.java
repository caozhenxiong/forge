package devflow.agent.supervisor;

import devflow.agent.executor.generation.GenerationFailureReport;

import devflow.agent.context.ContextAccessProfile;
import devflow.agent.context.ProjectedContext;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;

/**
 * 负责 supervisor 的生成失败恢复 prompt。
 *
 * <p>这层只组装“是否继续当前生成链”的上下文，
 * 不参与默认 fallback 与结果 sanitize。
 */
final class SupervisorGenerationRecoveryPromptBuilder {

    private final SupervisorArtifactRenderer artifactRenderer;
    private final SupervisorPromptTextSupport textSupport = new SupervisorPromptTextSupport();

    SupervisorGenerationRecoveryPromptBuilder(SupervisorArtifactRenderer artifactRenderer) {
        this.artifactRenderer = artifactRenderer;
    }

    String systemPrompt() {
        return """
                你是 SupervisorAgent，负责判断 IMPLEMENTATION 内部的生成失败是否应该继续重试。
                你必须只返回 JSON，格式如下：
                {
                  "action": "RETRY_SUBTASK|ROUTE_TO_REPAIR|FAIL_SUBTASK",
                  "reason": "一句话说明",
                  "focus": ["本轮必须优先处理的问题"],
                  "constraints": ["本轮额外约束"],
                  "requiredEvidence": ["下一轮必须补出的证据"],
                  "deliveryPolicy": {
                    "mode": "SKELETON|INCREMENTAL|PATCH|REWORK",
                    "maxFiles": 1,
                    "maxSymbols": 1,
                    "preferPreciseEditing": true,
                    "forceBacklogSplit": false,
                    "requireVerificationBeforeReview": true
                  }
                }

                规则：
                1. 你只负责决定是否继续调用模型，以及应该换成什么更保守的交付策略。
                2. 偶发的 patch JSON、符号匹配、tree-sitter 解析失败，优先 RETRY_SUBTASK。
                3. 如果连续失败且问题聚焦，优先 ROUTE_TO_REPAIR，并进一步收缩改动面。
                4. 只有在继续调用模型价值很低或明显无法收敛时，才选择 FAIL_SUBTASK。
                5. 如果当前是精确改写失败，优先继续保持 preferPreciseEditing=true，并缩小 maxFiles/maxSymbols；不要把局部编辑退化成整文件重写。
                6. 不要扩大改单范围，不要建议整轮重做整个产品。
                """;
    }

    String userPrompt(
            RunRecord runRecord,
            GenerationFailureReport failureReport,
            int subtaskAttempt,
            String subtaskTitle,
            String subtaskGoal,
            String feedback,
            ProjectedContext projectedContext,
            DocumentLanguage language,
            GenerationRecoveryDecision fallback
    ) {
        return """
                当前阶段：
                IMPLEMENTATION

                当前子任务：
                - title: %s
                - goal: %s
                - attempt: %d

                当前生成失败：
                %s

                当前反馈：
                %s

                Supervisor Context Slice：
                %s

                默认保守决策参考：
                - action: %s
                - reason: %s
                - deliveryPolicy: %s
                """.formatted(
                textSupport.blank(subtaskTitle),
                textSupport.blank(subtaskGoal),
                subtaskAttempt,
                failureReport == null ? "" : failureReport.toMarkdown(language),
                textSupport.shrink(feedback),
                projectedContext.toMarkdown(ContextAccessProfile.SUPERVISOR, language),
                fallback.action(),
                fallback.reason(),
                artifactRenderer.renderPolicy(fallback.deliveryPolicy())
        );
    }
}
