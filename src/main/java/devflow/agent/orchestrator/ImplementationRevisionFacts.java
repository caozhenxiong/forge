package devflow.agent.orchestrator;

import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
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
                && continuationContext.implementationPatchTarget().concretePatch()
                && !continuationContext.overrideChanges().isEmpty();
    }

    public boolean shouldAutoContinueCurrentImplementation() {
        return !stageReady && !blocked && (hasIncompleteSubtasks() || hasStructuredPatchScope());
    }

    public boolean shouldBlockForHumanReview() {
        return !stageReady && blocked;
    }

    public RevisionRoutingPlan toRevisionRoutingPlan() {
        if (!shouldAutoContinueCurrentImplementation()) {
            return RevisionRoutingPlan.none();
        }
        if (hasStructuredPatchScope()) {
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
