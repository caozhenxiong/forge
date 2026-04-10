package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.GenerationRecoveryDecision;

/**
 * 记录某个子任务的一次执行尝试。
 * 该对象统一承载自检、验证、生成失败和恢复决策，避免这些信息散落在执行器分支里。
 */
record SubtaskAttemptReport(
        int attempt,
        SelfCheckResult selfCheck,
        ReviewResult review,
        GenerationFailureReport generationFailure,
        GenerationRecoveryDecision recoveryDecision
) {
    static SubtaskAttemptReport fromVerification(
            int attempt,
            SelfCheckResult selfCheck,
            ReviewResult review
    ) {
        return new SubtaskAttemptReport(attempt, selfCheck, review, null, null);
    }

    static SubtaskAttemptReport fromGenerationFailure(
            int attempt,
            GenerationFailureReport generationFailure,
            GenerationRecoveryDecision recoveryDecision,
            DocumentLanguage language
    ) {
        return new SubtaskAttemptReport(
                attempt,
                new SelfCheckResult(
                        false,
                        language.choose("代码生成未通过本地校验", "Generated code did not pass local validation"),
                        generationFailure == null ? "" : generationFailure.toMarkdown(language)
                ),
                generationFailure == null
                        ? new ReviewResult(
                                ReviewDecision.REVISION_REQUIRED,
                                FixMode.PATCH,
                                language.choose("代码生成失败", "Code generation failed"),
                                language.choose("请缩小改动范围后重试", "Reduce the change scope and retry")
                        )
                        : generationFailure.toReviewResult(),
                generationFailure,
                recoveryDecision
        );
    }
}
