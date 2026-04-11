package devflow.agent.executor;

import devflow.agent.review.ReviewResult;

/**
 * 单次子任务尝试的结构化结果。
 * 外层执行器只消费这个对象来决定 approved/retry/recovery，不再自己拼一组原子引用。
 */
record SubtaskAttemptResult(
        GenerationFailureException generationFailure,
        SelfCheckResult selfCheck,
        ImplementationCompletenessGateOutcome completenessOutcome,
        ReviewResult verification,
        SubtaskRevisionDirective revisionDirective
) {
}
