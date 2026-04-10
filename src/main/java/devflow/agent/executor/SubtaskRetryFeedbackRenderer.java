package devflow.agent.executor;

import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.review.ReviewResult;
import java.util.List;

/**
 * 负责子任务 retry feedback 的结构化叙述渲染。
 *
 * <p>执行器与验证支撑不再自己拼接 retry 文本，只提供结构化 review/self-check/completeness 输入。
 */
final class SubtaskRetryFeedbackRenderer {

    private final SubtaskReviewPromptAssembler promptAssembler;

    SubtaskRetryFeedbackRenderer(SubtaskReviewPromptAssembler promptAssembler) {
        this.promptAssembler = promptAssembler;
    }

    String render(
            SelfCheckResult selfCheck,
            ReviewResult verification,
            ImplementationCompletenessResult completenessResult
    ) {
        return ExecutionDirectiveNarrativeRenderer.renderRetryFeedback(
                new ExecutionDirectivePayload(
                        verification.fixMode() == null ? null : verification.fixMode().name(),
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        verification.summary(),
                        verification.changeRequest(),
                        verification.evidence(),
                        verification.actionItems(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null
                ),
                selfCheck.summary(),
                selfCheck.details(),
                verification.summary(),
                verification.changeRequest(),
                completenessResult == null ? "" : completenessResult.summary(),
                completenessResult == null || completenessResult.evidenceMarkdown().isBlank()
                        ? promptAssembler.renderBulletList(List.of())
                        : completenessResult.evidenceMarkdown()
        );
    }
}
