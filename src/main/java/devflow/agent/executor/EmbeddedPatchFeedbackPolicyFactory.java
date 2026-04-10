package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 负责为宿主内嵌 patch 单元构造失败反馈策略。
 *
 * <p>脚本和样式虽然共用执行器，但反馈文案仍依赖嵌入类型。
 * 这层把 patchKind 相关的提示装配集中起来，避免执行器继续内联大段 lambda。
 */
final class EmbeddedPatchFeedbackPolicyFactory {

    PatchAttemptFeedbackPolicy create(Path relativePath, EditUnit unit, EmbeddedPatchKind patchKind) {
        return new PatchAttemptFeedbackPolicy(
                () -> EmbeddedPatchFeedbackRenderer.abortFeedback(
                        relativePath,
                        unit,
                        patchKind
                ),
                retryAttempt -> EmbeddedPatchFeedbackRenderer.truncationRetryFeedback(
                        retryAttempt,
                        relativePath,
                        unit,
                        patchKind
                ),
                retryAttempt -> EmbeddedPatchFeedbackRenderer.invalidJsonFeedback(
                        retryAttempt,
                        relativePath,
                        unit,
                        patchKind
                ),
                retryAttempt -> EmbeddedPatchFeedbackRenderer.patchSchemaFeedback(
                        retryAttempt,
                        relativePath,
                        unit,
                        patchKind
                ),
                retryAttempt -> EmbeddedPatchFeedbackRenderer.scopeViolationFeedback(
                        retryAttempt,
                        relativePath,
                        unit,
                        patchKind
                ),
                ignored -> EmbeddedPatchFeedbackRenderer.missingTargetFeedback(
                        relativePath,
                        unit,
                        patchKind
                )
        );
    }
}
