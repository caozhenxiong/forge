package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StructuredReviewResult;
import devflow.agent.review.SubtaskBoundaryReviewPayload;

/**
 * 子任务 capability boundary 的确定性 gate。
 *
 * <p>review provider 只负责给出 typed payload；真正的审批驳回在本地完成，
 * 避免再退回 prose-only 语义判断。
 */
final class SubtaskBoundaryGate {

    ReviewResult enforce(
            Subtask subtask,
            StructuredReviewResult structuredReview,
            DocumentLanguage language
    ) {
        if (structuredReview == null) {
            return null;
        }
        SubtaskBoundaryReviewPayload payload = structuredReview.subtaskBoundary();
        if (payload == null || !payload.provided() || !payload.boundaryViolation()) {
            return structuredReview.result();
        }
        String summary = blank(payload.summary(), language.choose(
                "当前子任务越过了 capability boundary，提前实现了后续或非本轮负责能力。",
                "The current subtask crossed the capability boundary and implemented deferred or foreign scope."
        ));
        String changeRequest = blank(payload.actionItems(), language.choose(
                "请移除当前子任务越界实现的后续/非本轮能力，只保留当前 ownedCapabilities 与 acceptanceCriteria 对应实现。",
                "Remove deferred or foreign-scope implementation from the current subtask and keep only the behavior required by ownedCapabilities and acceptanceCriteria."
        ));
        String evidence = mergeEvidence(structuredReview.result().evidence(), payload.evidence());
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                summary,
                changeRequest,
                evidence,
                blank(payload.actionItems(), structuredReview.result().actionItems()),
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                java.util.List.of(),
                structuredReview.result().revisionRoute(),
                ReviewReasonCode.CONTRACT_BOUNDARY_VIOLATION
        );
    }

    private String mergeEvidence(String reviewEvidence, String payloadEvidence) {
        String left = reviewEvidence == null ? "" : reviewEvidence.trim();
        String right = payloadEvidence == null ? "" : payloadEvidence.trim();
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank() || right.equals(left)) {
            return left;
        }
        return left + "\n" + right;
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
