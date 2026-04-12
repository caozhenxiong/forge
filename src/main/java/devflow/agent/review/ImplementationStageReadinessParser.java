package devflow.agent.review;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;

/**
 * 解析 implementation artifact 里的就绪标记。
 *
 * <p>这部分逻辑是纯结构化解析，不属于 reviewer 的语义判断职责，
 * 因此单独下沉，避免 StageReviewer 继续同时承担 parser 和 reviewer 两种角色。
 */
public final class ImplementationStageReadinessParser {

    public ImplementationStageReadiness parse(String artifactContent) {
        ImplementationStageStatusPayload payload = StructuredArtifactBlocks.readFirstJsonBlock(
                artifactContent,
                ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                ImplementationStageStatusPayload.class
        );
        if (payload == null) {
            return ImplementationStageReadiness.ready();
        }
        if (payload.stageReady()) {
            return ImplementationStageReadiness.ready();
        }
        if (payload.continuationMode() == ImplementationContinuationMode.BLOCK_STAGE) {
            return ImplementationStageReadiness.blocked(
                    payload.continuationSummary(),
                    payload.continuationChangeRequest(),
                    payload.continuationEvidence(),
                    payload.continuationActionItems(),
                    payload.continuationPatchTarget(),
                    payload.continuationReasonCode()
            );
        }
        ImplementationPatchTarget continuationPatchTarget = payload.continuationPatchTarget() == null
                ? ImplementationPatchTarget.NONE
                : payload.continuationPatchTarget();
        if (continuationPatchTarget.concretePatch()
                || hasText(payload.continuationSummary())
                || hasText(payload.continuationChangeRequest())
                || hasText(payload.continuationEvidence())
                || hasText(payload.continuationActionItems())
                || payload.continuationReasonCode() != null && payload.continuationReasonCode() != ReviewReasonCode.NONE) {
            return ImplementationStageReadiness.continuation(
                    payload.continuationMode(),
                    payload.continuationSummary(),
                    payload.continuationChangeRequest(),
                    payload.continuationEvidence(),
                    payload.continuationActionItems(),
                    continuationPatchTarget,
                    payload.continuationReasonCode()
            );
        }
        String incomplete = payload.incompleteSubtasks() == null || payload.incompleteSubtasks().isEmpty()
                ? ""
                : String.join("；", payload.incompleteSubtasks());
        if (payload.planCompleted() && !payload.architectCheckPassed()) {
            String reason = payload.architectFailureReason() == null || payload.architectFailureReason().isBlank()
                    ? ""
                    : " failureReason=" + payload.architectFailureReason().trim() + "。";
            String details = payload.architectFailureDetails() == null || payload.architectFailureDetails().isBlank()
                    ? ""
                    : " details=" + payload.architectFailureDetails().trim();
            String evidence = incomplete == null || incomplete.isBlank()
                    ? "当前实现计划已执行完毕，但整体可运行契约或模块接线检查未通过。" + reason + details
                    : "当前实现计划已执行完毕，但整体可运行契约或模块接线检查未通过；报告中的未就绪项：" + incomplete.trim() + "。" + reason + details;
            return ImplementationStageReadiness.architectCheckFailed(
                    evidence,
                    parsePatchTarget(payload.implementationPatchTarget())
            );
        }
        String normalizedIncomplete = incomplete == null || incomplete.isBlank()
                ? "当前实现计划仍有未执行或未完成的子任务。"
                : "未完成子任务：" + incomplete.trim();
        return ImplementationStageReadiness.incomplete(normalizedIncomplete);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ImplementationPatchTarget parsePatchTarget(String value) {
        if (value == null || value.isBlank()) {
            return ImplementationPatchTarget.NONE;
        }
        try {
            return ImplementationPatchTarget.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return ImplementationPatchTarget.NONE;
        }
    }
}
