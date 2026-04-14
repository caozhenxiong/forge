package devflow.agent.orchestrator;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.editing.FileEditScope;
import devflow.agent.executor.runtime.RuntimeOwnershipMode;
import devflow.agent.protocol.FileChangePayload;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.util.EnumParsers;
import java.util.List;

/**
 * implementation continuation payload 的唯一解析入口。
 */
final class ImplementationContinuationSupport {

    StageContinuationContext toContinuationContext(ImplementationStageStatusPayload payload) {
        if (payload == null) {
            throw new IllegalStateException("Invalid implementation continuation payload: payload is missing.");
        }
        return new StageContinuationContext(
                requiredField("continuationSummary", payload.continuationSummary()),
                requiredField("continuationChangeRequest", payload.continuationChangeRequest()),
                requiredField("continuationEvidence", payload.continuationEvidence()),
                requiredField("continuationActionItems", payload.continuationActionItems()),
                overrideChanges(payload.continuationOverrideChanges()),
                requiredPatchTarget(payload),
                requiredReasonCode(payload)
        );
    }

    ReviewResult toHumanReviewResult(StageContinuationContext context) {
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                context.summary(),
                context.changeRequest(),
                context.evidence(),
                context.actionItems(),
                context.implementationPatchTarget(),
                context.overrideChanges(),
                ReviewRevisionRoute.REQUEST_HUMAN,
                context.reasonCode()
        );
    }

    private List<FileChange> overrideChanges(List<FileChangePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        return payloads.stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .map(this::toFileChange)
                .toList();
    }

    private FileChange toFileChange(FileChangePayload payload) {
        return new FileChange(
                payload.path(),
                EnumParsers.parseIgnoreCase(ChangeAction.class, payload.action(), ChangeAction.WRITE),
                payload.reason() == null ? "" : payload.reason(),
                EnumParsers.parseIgnoreCase(FileEditScope.class, payload.editScope(), FileEditScope.AUTO),
                EnumParsers.parseIgnoreCase(RuntimeOwnershipMode.class, payload.runtimeOwnership(), null),
                Boolean.TRUE.equals(payload.hostHtmlPatchRequired())
        );
    }

    private String requiredField(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Invalid implementation continuation payload: missing " + fieldName + ".");
        }
        return value.trim();
    }

    private ImplementationPatchTarget requiredPatchTarget(ImplementationStageStatusPayload payload) {
        if (payload.continuationPatchTarget() == null) {
            throw new IllegalStateException("Invalid implementation continuation payload: missing continuationPatchTarget.");
        }
        return payload.continuationPatchTarget();
    }

    private ReviewReasonCode requiredReasonCode(ImplementationStageStatusPayload payload) {
        if (payload.continuationReasonCode() == null) {
            throw new IllegalStateException("Invalid implementation continuation payload: missing continuationReasonCode.");
        }
        return payload.continuationReasonCode();
    }
}
