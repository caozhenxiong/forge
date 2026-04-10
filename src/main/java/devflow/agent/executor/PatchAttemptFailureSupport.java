package devflow.agent.executor;

/**
 * 统一处理 patch 单元执行期的结构化失败。
 *
 * <p>这层负责把：
 * 1. LLM/patch 异常；
 * 2. 失败类型分类；
 * 3. patch router 的 abort/retry 判断；
 * 4. 反馈策略；
 * 统一收口成 `GenerationAttemptResult`。
 *
 * <p>执行器只保留各自的 patch 语义，不再复制同一套 failure routing 分支。
 */
final class PatchAttemptFailureSupport {

    private final GenerationFailureClassifier generationFailureClassifier;
    private final PatchFailureRouter patchFailureRouter;

    PatchAttemptFailureSupport(
            GenerationFailureClassifier generationFailureClassifier,
            PatchFailureRouter patchFailureRouter
    ) {
        this.generationFailureClassifier = generationFailureClassifier;
        this.patchFailureRouter = patchFailureRouter;
    }

    GenerationAttemptResult<String> handle(Exception exception, int attempt, EditUnit unit, PatchAttemptFeedbackPolicy feedbackPolicy)
            throws Exception {
        GenerationFailureType failureType = generationFailureClassifier.classify(exception);
        String evidence = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        PatchFailure patchFailure = PatchFailure.of(failureType, evidence);
        if (patchFailureRouter.shouldAbortCurrentUnit(unit, patchFailure)) {
            return GenerationAttemptResult.terminalFailure(
                    patchFailure,
                    feedbackPolicy.abortFeedback().get()
            );
        }
        if (failureType == GenerationFailureType.OUTPUT_TRUNCATED
                && patchFailureRouter.shouldRetryCurrentUnit(unit, patchFailure)) {
            return GenerationAttemptResult.failure(
                    patchFailure,
                    feedbackPolicy.truncationRetryFeedback().apply(attempt)
            );
        }
        if (failureType == GenerationFailureType.INVALID_PATCH_JSON) {
            return GenerationAttemptResult.failure(
                    patchFailure,
                    feedbackPolicy.invalidJsonFeedback().apply(attempt)
            );
        }
        if (failureType == GenerationFailureType.PATCH_SCHEMA_INVALID) {
            return GenerationAttemptResult.failure(
                    patchFailure,
                    feedbackPolicy.patchSchemaFeedback().apply(attempt)
            );
        }
        if (failureType == GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION) {
            return GenerationAttemptResult.failure(
                    patchFailure,
                    feedbackPolicy.scopeViolationFeedback().apply(attempt)
            );
        }
        if (failureType == GenerationFailureType.SYMBOL_NOT_FOUND) {
            return GenerationAttemptResult.terminalFailure(
                    patchFailure,
                    feedbackPolicy.symbolNotFoundFeedback().apply(evidence)
            );
        }
        throw exception;
    }
}
