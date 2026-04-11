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
        boolean architectCheckPassed,
        String architectFailureReason,
        String architectFailureDetails,
        String implementationPatchTarget,
        List<String> incompleteSubtasks,
        ImplementationContinuationMode continuationMode,
        String continuationSummary,
        String continuationChangeRequest,
        String continuationEvidence,
        String continuationActionItems,
        ImplementationPatchTarget continuationPatchTarget,
        ReviewReasonCode continuationReasonCode
) {
    public ImplementationStageStatusPayload(
            boolean stageReady,
            boolean planCompleted,
            boolean architectCheckPassed,
            List<String> incompleteSubtasks
    ) {
        this(
                stageReady,
                planCompleted,
                architectCheckPassed,
                "",
                "",
                "",
                incompleteSubtasks,
                ImplementationContinuationMode.CONTINUE_SUBTASKS,
                "",
                "",
                "",
                "",
                ImplementationPatchTarget.NONE,
                ReviewReasonCode.NONE
        );
    }
}
