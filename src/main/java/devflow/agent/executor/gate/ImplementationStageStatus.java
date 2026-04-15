package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import java.util.List;

/**
 * 表示 implementation 阶段的整体完成状态。
 *
 * <p>这里的 contract gate 结果是 implementation 阶段唯一允许对外传播的契约判定源。
 * progress、report、state snapshot、resume 都只能消费这一份结果，不能再各自重算或拼字面量。
 */
public record ImplementationStageStatus(
        int plannedSubtasks,
        int executedSubtasks,
        int completedSubtasks,
        boolean planCompleted,
        boolean stageReady,
        List<String> incompleteSubtasks,
        ArchitectIntegrationCheckResult contractGateResult,
        ImplementationContinuationMode continuationMode,
        String continuationSummary,
        String continuationChangeRequest,
        String continuationEvidence,
        String continuationActionItems,
        List<FileChange> continuationOverrideChanges,
        ImplementationPatchTarget continuationPatchTarget,
        ReviewReasonCode continuationReasonCode
) {
    public ImplementationStageStatus {
        incompleteSubtasks = incompleteSubtasks == null ? List.of() : List.copyOf(incompleteSubtasks);
        continuationMode = continuationMode == null
                ? ImplementationContinuationMode.MID_PLAN_CONTINUE
                : continuationMode;
        continuationSummary = continuationSummary == null ? "" : continuationSummary;
        continuationChangeRequest = continuationChangeRequest == null ? "" : continuationChangeRequest;
        continuationEvidence = continuationEvidence == null ? "" : continuationEvidence;
        continuationActionItems = continuationActionItems == null ? "" : continuationActionItems;
        continuationOverrideChanges = continuationOverrideChanges == null ? List.of() : List.copyOf(continuationOverrideChanges);
        continuationPatchTarget = continuationPatchTarget == null
                ? ImplementationPatchTarget.NONE
                : continuationPatchTarget;
        continuationReasonCode = continuationReasonCode == null
                ? ReviewReasonCode.NONE
                : continuationReasonCode;
    }

    public ImplementationStageStatus(
            int plannedSubtasks,
            int executedSubtasks,
            int completedSubtasks,
            boolean planCompleted,
            boolean stageReady,
            List<String> incompleteSubtasks
    ) {
        this(
                plannedSubtasks,
                executedSubtasks,
                completedSubtasks,
                planCompleted,
                stageReady,
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

    public boolean contractGatePassed() {
        return contractGateResult == null || contractGateResult.passed();
    }

    public boolean blockedForHuman() {
        return continuationMode.blocked();
    }

    public boolean hasContinuationDirective() {
        return blockedForHuman()
                || continuationPatchTarget.concretePatch()
                || continuationReasonCode != ReviewReasonCode.NONE
                || !continuationSummary.isBlank()
                || !continuationChangeRequest().isBlank()
                || !continuationEvidence().isBlank()
                || !continuationActionItems().isBlank()
                || !continuationOverrideChanges.isEmpty();
    }
}
