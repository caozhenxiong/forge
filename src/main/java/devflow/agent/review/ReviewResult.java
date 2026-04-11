package devflow.agent.review;

import devflow.agent.executor.FileChange;
import java.util.List;

public record ReviewResult(
        ReviewDecision decision,
        FixMode fixMode,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems,
        ImplementationPatchTarget implementationPatchTarget,
        List<FileChange> overrideChanges,
        ReviewRevisionRoute revisionRoute,
        ReviewReasonCode reasonCode
) {
    public ReviewResult {
        implementationPatchTarget = implementationPatchTarget == null
                ? ImplementationPatchTarget.NONE
                : implementationPatchTarget;
        overrideChanges = overrideChanges == null ? List.of() : List.copyOf(overrideChanges);
        revisionRoute = revisionRoute == null ? ReviewRevisionRoute.PATCH_CURRENT_STAGE : revisionRoute;
        reasonCode = reasonCode == null ? ReviewReasonCode.NONE : reasonCode;
    }

    public ReviewResult(ReviewDecision decision, FixMode fixMode, String summary, String changeRequest) {
        this(decision, fixMode, summary, changeRequest, "", "", ImplementationPatchTarget.NONE, List.of(),
                ReviewRevisionRoute.PATCH_CURRENT_STAGE, ReviewReasonCode.NONE);
    }

    public ReviewResult(
            ReviewDecision decision,
            FixMode fixMode,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems
    ) {
        this(decision, fixMode, summary, changeRequest, evidence, actionItems, ImplementationPatchTarget.NONE, List.of(),
                ReviewRevisionRoute.PATCH_CURRENT_STAGE, ReviewReasonCode.NONE);
    }

    public ReviewResult(
            ReviewDecision decision,
            FixMode fixMode,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            ImplementationPatchTarget implementationPatchTarget
    ) {
        this(decision, fixMode, summary, changeRequest, evidence, actionItems, implementationPatchTarget, List.of(),
                ReviewRevisionRoute.PATCH_CURRENT_STAGE, ReviewReasonCode.NONE);
    }

    public ReviewResult(
            ReviewDecision decision,
            FixMode fixMode,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            ImplementationPatchTarget implementationPatchTarget,
            List<FileChange> overrideChanges
    ) {
        this(decision, fixMode, summary, changeRequest, evidence, actionItems, implementationPatchTarget, overrideChanges,
                ReviewRevisionRoute.PATCH_CURRENT_STAGE, ReviewReasonCode.NONE);
    }
}
