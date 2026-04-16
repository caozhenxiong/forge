package devflow.agent.domain;

import devflow.agent.executor.FileChange;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;

public record HumanReviewResolutionContext(
        HumanReviewIntent intent,
        StageType targetStage,
        FixMode fixMode,
        ImplementationPatchTarget implementationPatchTarget,
        List<FileChange> overrideChanges,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems,
        ImplementationContinuationMode continuationMode,
        boolean terminal,
        String diagnosticMessage
) {

    public HumanReviewResolutionContext {
        intent = intent == null ? HumanReviewIntent.APPROVE_STAGE_GATE : intent;
        fixMode = fixMode == null ? FixMode.NONE : fixMode;
        implementationPatchTarget = implementationPatchTarget == null
                ? ImplementationPatchTarget.NONE
                : implementationPatchTarget;
        overrideChanges = overrideChanges == null ? List.of() : List.copyOf(overrideChanges);
        summary = summary == null ? "" : summary.trim();
        changeRequest = changeRequest == null ? "" : changeRequest.trim();
        evidence = evidence == null ? "" : evidence.trim();
        actionItems = actionItems == null ? "" : actionItems.trim();
        diagnosticMessage = diagnosticMessage == null ? "" : diagnosticMessage.trim();
    }

    public static HumanReviewResolutionContext approveStageGate(String summary, String changeRequest) {
        return new HumanReviewResolutionContext(
                HumanReviewIntent.APPROVE_STAGE_GATE,
                null,
                FixMode.NONE,
                ImplementationPatchTarget.NONE,
                List.of(),
                summary,
                changeRequest,
                "",
                "",
                null,
                false,
                ""
        );
    }

    public static HumanReviewResolutionContext confirmRepairRoute(
            StageType targetStage,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            List<FileChange> overrideChanges,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            ImplementationContinuationMode continuationMode
    ) {
        return new HumanReviewResolutionContext(
                HumanReviewIntent.CONFIRM_REPAIR_ROUTE,
                targetStage,
                fixMode,
                implementationPatchTarget,
                overrideChanges,
                summary,
                changeRequest,
                evidence,
                actionItems,
                continuationMode,
                false,
                ""
        );
    }

    public HumanReviewResolutionContext asTerminal(String diagnosticMessage) {
        return new HumanReviewResolutionContext(
                intent,
                targetStage,
                fixMode,
                implementationPatchTarget,
                overrideChanges,
                summary,
                changeRequest,
                evidence,
                actionItems,
                continuationMode,
                true,
                diagnosticMessage
        );
    }

    public boolean confirmsRepairRoute() {
        return intent == HumanReviewIntent.CONFIRM_REPAIR_ROUTE;
    }
}
