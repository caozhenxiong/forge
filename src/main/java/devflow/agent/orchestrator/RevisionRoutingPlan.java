package devflow.agent.orchestrator;

import devflow.agent.executor.FileChange;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;

/**
 * FlowController 产出的唯一修订执行计划。
 *
 * <p>这层把“当前应该按 PATCH continuation 还是 fresh replan 执行”收成单点，
 * FlowDecisionExecutor 只消费它，不再二次解释。
 */
public record RevisionRoutingPlan(
        FixMode fixMode,
        ImplementationPatchTarget implementationPatchTarget,
        List<FileChange> overrideChanges
) {

    private static final RevisionRoutingPlan NONE =
            new RevisionRoutingPlan(null, ImplementationPatchTarget.NONE, List.of());

    public RevisionRoutingPlan {
        implementationPatchTarget = implementationPatchTarget == null
                ? ImplementationPatchTarget.NONE
                : implementationPatchTarget;
        overrideChanges = overrideChanges == null ? List.of() : List.copyOf(overrideChanges);
    }

    public static RevisionRoutingPlan none() {
        return NONE;
    }

    public boolean active() {
        return fixMode != null;
    }
}
