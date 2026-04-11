package devflow.agent.protocol;

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
        List<String> incompleteSubtasks
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
                incompleteSubtasks
        );
    }
}
