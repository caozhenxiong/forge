package devflow.agent.executor;

import devflow.agent.review.ReviewResult;
import java.util.List;

/**
 * 聚合一次子任务尝试在 turn loop 中产生的中间结果。
 */
final class SubtaskAttemptProgress {

    private GenerationFailureException generationFailure;
    private SelfCheckResult selfCheck;
    private List<ToolResult> selfCheckToolResults = List.of();
    private ImplementationCompletenessGateOutcome completenessOutcome;
    private ReviewResult verification;
    private SubtaskRevisionDirective revisionDirective = SubtaskRevisionDirective.empty();

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

    List<ToolResult> selfCheckToolResults() {
        return selfCheckToolResults;
    }

    void selfCheckToolResults(List<ToolResult> selfCheckToolResults) {
        this.selfCheckToolResults = selfCheckToolResults == null ? List.of() : List.copyOf(selfCheckToolResults);
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

    SubtaskRevisionDirective revisionDirective() {
        return revisionDirective;
    }

    void revisionDirective(SubtaskRevisionDirective revisionDirective) {
        this.revisionDirective = revisionDirective == null ? SubtaskRevisionDirective.empty() : revisionDirective;
    }

    SubtaskAttemptResult toResult() {
        return new SubtaskAttemptResult(
                generationFailure,
                selfCheck,
                selfCheckToolResults,
                completenessOutcome,
                verification,
                revisionDirective
        );
    }
}
