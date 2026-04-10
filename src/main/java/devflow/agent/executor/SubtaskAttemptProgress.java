package devflow.agent.executor;

import devflow.agent.review.ReviewResult;

/**
 * 聚合一次子任务尝试在 turn loop 中产生的中间结果。
 */
final class SubtaskAttemptProgress {

    private GenerationFailureException generationFailure;
    private SelfCheckResult selfCheck;
    private ImplementationCompletenessGateOutcome completenessOutcome;
    private ReviewResult verification;

    GenerationFailureException generationFailure() {
        return generationFailure;
    }

    void generationFailure(GenerationFailureException generationFailure) {
        this.generationFailure = generationFailure;
    }

    SelfCheckResult selfCheck() {
        return selfCheck;
    }

    void selfCheck(SelfCheckResult selfCheck) {
        this.selfCheck = selfCheck;
    }

    ImplementationCompletenessGateOutcome completenessOutcome() {
        return completenessOutcome;
    }

    void completenessOutcome(ImplementationCompletenessGateOutcome completenessOutcome) {
        this.completenessOutcome = completenessOutcome;
    }

    ReviewResult verification() {
        return verification;
    }

    void verification(ReviewResult verification) {
        this.verification = verification;
    }

    SubtaskAttemptResult toResult() {
        return new SubtaskAttemptResult(generationFailure, selfCheck, completenessOutcome, verification);
    }
}
