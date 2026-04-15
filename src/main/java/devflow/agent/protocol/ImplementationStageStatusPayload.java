package devflow.agent.protocol;

import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import java.util.List;

/**
 * implementation artifact 中“当前是否可进入 implementation review”的机器协议。
 */
public record ImplementationStageStatusPayload(
        boolean stageReady,
        boolean planCompleted,
        List<String> incompleteSubtasks,
        ContractGatePayload contractGate,
        ImplementationContinuationMode continuationMode,
        String continuationSummary,
        String continuationChangeRequest,
        String continuationEvidence,
        String continuationActionItems,
        List<FileChangePayload> continuationOverrideChanges,
        ImplementationPatchTarget continuationPatchTarget,
        ReviewReasonCode continuationReasonCode
) {
    public ImplementationStageStatusPayload {
        incompleteSubtasks = incompleteSubtasks == null ? List.of() : List.copyOf(incompleteSubtasks);
        continuationOverrideChanges = continuationOverrideChanges == null ? List.of() : List.copyOf(continuationOverrideChanges);
    }

    public ImplementationStageStatusPayload(
            boolean stageReady,
            boolean planCompleted,
            List<String> incompleteSubtasks
    ) {
        this(
                stageReady,
                planCompleted,
                incompleteSubtasks,
                null,
                ImplementationContinuationMode.MID_PLAN_CONTINUE,
                "",
                "",
                "",
                "",
                List.of(),
                ImplementationPatchTarget.NONE,
                ReviewReasonCode.NONE
        );
    }

    public record ContractGatePayload(
            String scope,
            boolean passed,
            String failureReason,
            String details,
            String patchTarget,
            RuntimeContractPayload runtimeContract
    ) {
    }

    public record RuntimeContractPayload(
            String htmlEntryPath,
            String runtimeOwnership,
            List<String> runtimePaths
    ) {
    }
}
