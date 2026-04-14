package devflow.agent.orchestrator;

import devflow.agent.executor.FileChange;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import java.util.List;

/**
 * implementation continuation 的唯一结构化上下文。
 */
public record StageContinuationContext(
        String summary,
        String changeRequest,
        String evidence,
        String actionItems,
        List<FileChange> overrideChanges,
        ImplementationPatchTarget implementationPatchTarget,
        ReviewReasonCode reasonCode
) {
    public StageContinuationContext {
        summary = summary == null ? "" : summary.trim();
        changeRequest = changeRequest == null ? "" : changeRequest.trim();
        evidence = evidence == null ? "" : evidence.trim();
        actionItems = actionItems == null ? "" : actionItems.trim();
        overrideChanges = overrideChanges == null ? List.of() : List.copyOf(overrideChanges);
        implementationPatchTarget = implementationPatchTarget == null
                ? ImplementationPatchTarget.NONE
                : implementationPatchTarget;
        reasonCode = reasonCode == null ? ReviewReasonCode.NONE : reasonCode;
    }
}
