package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 负责为代码 patch 单元构造失败反馈策略。
 *
 * <p>执行器只需要声明“当前单元失败了”，具体每种失败回什么 retry/abort 提示
 * 统一在这里装配，避免 patch 主链再次回到大执行器里堆反馈分支。
 */
final class CodePatchFeedbackPolicyFactory {

    PatchAttemptFeedbackPolicy create(Path relativePath, EditUnit unit, int attempt) {
        return new PatchAttemptFeedbackPolicy(
                () -> CodePatchFeedbackRenderer.abortFeedback(relativePath, unit),
                retryAttempt -> CodePatchFeedbackRenderer.truncationRetryFeedback(
                        retryAttempt,
                        relativePath,
                        unit
                ),
                retryAttempt -> CodePatchFeedbackRenderer.invalidJsonFeedback(
                        retryAttempt,
                        relativePath,
                        unit
                ),
                retryAttempt -> CodePatchFeedbackRenderer.patchSchemaFeedback(
                        retryAttempt,
                        relativePath,
                        unit
                ),
                retryAttempt -> CodePatchFeedbackRenderer.scopeViolationFeedback(
                        retryAttempt,
                        relativePath,
                        unit
                ),
                evidence -> CodePatchFeedbackRenderer.symbolNotFoundFeedback(
                        attempt,
                        relativePath,
                        unit,
                        evidence
                )
        );
    }
}
