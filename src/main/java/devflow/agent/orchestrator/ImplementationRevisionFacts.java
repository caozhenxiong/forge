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
        List<String> incompleteSubtasks,
        StageContinuationContext continuationContext
) {

    private static final ImplementationRevisionFacts NONE =
            new ImplementationRevisionFacts(true, List.of(), null);

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
                && continuationMode().patchContinue()
                && continuationContext.implementationPatchTarget().concretePatch()
                && !continuationContext.overrideChanges().isEmpty();
    }

    public boolean shouldAutoContinueCurrentImplementation() {
        return !stageReady
                && continuationContext != null
                && continuationMode().autoContinue();
    }

    public boolean shouldMidPlanContinueCurrentImplementation() {
        return shouldAutoContinueCurrentImplementation()
                && continuationMode() == ImplementationContinuationMode.MID_PLAN_CONTINUE;
    }

    public boolean shouldPatchContinueCurrentImplementation() {
        return shouldAutoContinueCurrentImplementation()
                && continuationMode() == ImplementationContinuationMode.PATCH_CONTINUE;
    }

    public boolean shouldBlockForHumanReview() {
        return !stageReady && continuationContext != null && continuationMode().blocked();
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
        if (continuationMode() == ImplementationContinuationMode.PATCH_CONTINUE) {
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

    public ImplementationContinuationMode continuationMode() {
        return continuationContext == null
                ? ImplementationContinuationMode.MID_PLAN_CONTINUE
                : continuationContext.continuationMode();
    }
}
