package devflow.agent.orchestrator;

import devflow.agent.domain.StageType;
import devflow.agent.executor.FileChange;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.supervisor.SupervisorDecision;
import java.util.List;

public record RevisionContext(
        ReviewDecision decision,
        FixMode fixMode,
        ImplementationPatchTarget implementationPatchTarget,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems,
        List<FileChange> overrideChanges,
        SupervisorDecision supervisorDecision,
        StageType rerouteStage,
        boolean forceRepair,
        boolean repeatedIssue
) {
    public RevisionContext {
        implementationPatchTarget = implementationPatchTarget == null
                ? ImplementationPatchTarget.NONE
                : implementationPatchTarget;
        overrideChanges = overrideChanges == null ? List.of() : List.copyOf(overrideChanges);
    }
}
