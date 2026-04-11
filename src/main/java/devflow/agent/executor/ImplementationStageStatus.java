package devflow.agent.executor;

import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import java.util.List;

/**
 * 表示 implementation 阶段的整体完成状态。
 * 它只回答“计划是否完成、阶段是否可继续推进”，不负责生成 markdown。
 */
record ImplementationStageStatus(
        int plannedSubtasks,
        int executedSubtasks,
        int completedSubtasks,
        boolean planCompleted,
        boolean architectCheckPassed,
        boolean stageReady,
        List<String> incompleteSubtasks,
        ImplementationContinuationMode continuationMode,
        String continuationSummary,
        String continuationChangeRequest,
        String continuationEvidence,
        String continuationActionItems,
        ImplementationPatchTarget continuationPatchTarget,
        ReviewReasonCode continuationReasonCode
) {
    ImplementationStageStatus {
        incompleteSubtasks = incompleteSubtasks == null ? List.of() : List.copyOf(incompleteSubtasks);
        continuationMode = continuationMode == null
                ? ImplementationContinuationMode.CONTINUE_SUBTASKS
                : continuationMode;
        continuationSummary = continuationSummary == null ? "" : continuationSummary;
        continuationChangeRequest = continuationChangeRequest == null ? "" : continuationChangeRequest;
        continuationEvidence = continuationEvidence == null ? "" : continuationEvidence;
        continuationActionItems = continuationActionItems == null ? "" : continuationActionItems;
        continuationPatchTarget = continuationPatchTarget == null
                ? ImplementationPatchTarget.NONE
                : continuationPatchTarget;
        continuationReasonCode = continuationReasonCode == null
                ? ReviewReasonCode.NONE
                : continuationReasonCode;
    }

    ImplementationStageStatus(
            int plannedSubtasks,
            int executedSubtasks,
            int completedSubtasks,
            boolean planCompleted,
            boolean architectCheckPassed,
            boolean stageReady,
            List<String> incompleteSubtasks
    ) {
        this(
                plannedSubtasks,
                executedSubtasks,
                completedSubtasks,
                planCompleted,
                architectCheckPassed,
                stageReady,
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

    boolean blockedForHuman() {
        return continuationMode == ImplementationContinuationMode.BLOCK_STAGE;
    }
}
