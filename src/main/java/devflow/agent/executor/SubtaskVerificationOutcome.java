package devflow.agent.executor;

import devflow.agent.review.ReviewResult;

/**
 * 子任务验证输出。
 *
 * <p>review 仍承载审批语义；revisionDirective 只承载下一轮执行约束。
 * 两者必须分开，避免再把执行策略混回 review prose。
 */
record SubtaskVerificationOutcome(
        ReviewResult review,
        SubtaskRevisionDirective revisionDirective
) {

    static SubtaskVerificationOutcome of(ReviewResult review) {
        return new SubtaskVerificationOutcome(
                review,
                review == null
                        ? SubtaskRevisionDirective.empty()
                        : review.fixMode() == devflow.agent.review.FixMode.PATCH
                                ? SubtaskRevisionDirective.patch(review.overrideChanges())
                                : SubtaskRevisionDirective.retry(review.overrideChanges())
        );
    }

    static SubtaskVerificationOutcome of(ReviewResult review, SubtaskRevisionDirective revisionDirective) {
        return new SubtaskVerificationOutcome(
                review,
                revisionDirective == null ? SubtaskRevisionDirective.empty() : revisionDirective
        );
    }
}
