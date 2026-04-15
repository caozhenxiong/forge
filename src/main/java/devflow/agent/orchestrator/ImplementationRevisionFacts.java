package devflow.agent.orchestrator;

import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.protocol.ImplementationContinuationMode;
import java.util.List;

/**
 * FlowController 用来判断 implementation 回流语义的唯一结构化输入。
 */
public record ImplementationRevisionFacts(
        boolean stageReady,
        boolean blocked,
        List<String> incompleteSubtasks,
        StageContinuationContext continuationContext
) {

    private static final ImplementationRevisionFacts NONE =
            new ImplementationRevisionFacts(true, false, List.of(), null);

    public ImplementationRevisionFacts {
        incompleteSubtasks = incompleteSubtasks == null ? List.of() : List.copyOf(incompleteSubtasks);
    }

    public static ImplementationRevisionFacts none() {
        return NONE;
    }

    public boolean hasIncompleteSubtasks() {
        return !incompleteSubtasks.isEmpty();
    }

    public boolean hasContinuationContext() {
        return continuationContext != null;
    }

    public boolean hasStructuredPatchScope() {
        return continuationContext != null
                && continuationContext.continuationMode().patchContinue()
                && continuationContext.implementationPatchTarget().concretePatch()
                && !continuationContext.overrideChanges().isEmpty();
    }

    public boolean shouldAutoContinueCurrentImplementation() {
        return !stageReady
                && !blocked
                && continuationContext != null
                && continuationContext.continuationMode().autoContinue();
    }

    public boolean shouldBlockForHumanReview() {
        return !stageReady && blocked;
    }

    public ReviewResult blockedReviewResult() {
        if (!shouldBlockForHumanReview() || continuationContext == null) {
            return null;
        }
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                continuationContext.summary(),
                continuationContext.changeRequest(),
                continuationContext.evidence(),
                continuationContext.actionItems(),
                continuationContext.implementationPatchTarget(),
                continuationContext.overrideChanges(),
                ReviewRevisionRoute.REQUEST_HUMAN,
                continuationContext.reasonCode() == null ? ReviewReasonCode.NONE : continuationContext.reasonCode()
        );
    }

    public RevisionRoutingPlan toRevisionRoutingPlan() {
        if (!shouldAutoContinueCurrentImplementation()) {
            return RevisionRoutingPlan.none();
        }
        if (continuationContext.continuationMode() == ImplementationContinuationMode.PATCH_CONTINUE) {
            return new RevisionRoutingPlan(
                    FixMode.PATCH,
                    continuationContext.implementationPatchTarget(),
                    continuationContext.overrideChanges()
            );
        }
        return new RevisionRoutingPlan(
                FixMode.PATCH,
                ImplementationPatchTarget.NONE,
                List.of()
        );
    }
}
