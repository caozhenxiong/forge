package devflow.agent.executor.implementation.render;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;

/**
 * implementation 阶段整体状态的唯一结构化渲染器。
 *
 * <p>implementation markdown、独立 stage-status 辅助产物都必须复用这里，
 * 避免 live gate 和展示文档再次各写一套状态序列化逻辑。
 */
final class ImplementationStageStatusArtifactRenderer {

    String renderBlock(ImplementationStageStatus stageStatus) {
        return StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                toPayload(stageStatus)
        );
    }

    ImplementationStageStatusPayload toPayload(ImplementationStageStatus stageStatus) {
        if (stageStatus == null) {
            return new ImplementationStageStatusPayload(false, false, java.util.List.of());
        }
        return new ImplementationStageStatusPayload(
                stageStatus.stageReady(),
                stageStatus.planCompleted(),
                stageStatus.incompleteSubtasks(),
                serializeContractGate(stageStatus.contractGateResult()),
                stageStatus.continuationMode(),
                stageStatus.continuationSummary(),
                stageStatus.continuationChangeRequest(),
                stageStatus.continuationEvidence(),
                stageStatus.continuationActionItems(),
                stageStatus.continuationOverrideChanges().stream().map(this::toPayload).toList(),
                stageStatus.continuationPatchTarget(),
                stageStatus.continuationReasonCode()
        );
    }

    private ImplementationStageStatusPayload.ContractGatePayload serializeContractGate(
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (contractGateResult == null) {
            return null;
        }
        return new ImplementationStageStatusPayload.ContractGatePayload(
                contractGateResult.scope() == null ? null : contractGateResult.scope().name(),
                contractGateResult.passed(),
                contractGateResult.failureReason() == null ? null : contractGateResult.failureReason().name(),
                contractGateResult.details(),
                contractGateResult.implementationPatchTarget() == null
                        ? null
                        : contractGateResult.implementationPatchTarget().name(),
                contractGateResult.runtimeContract() == null
                        ? null
                        : new ImplementationStageStatusPayload.RuntimeContractPayload(
                                contractGateResult.runtimeContract().htmlEntryPath() == null
                                        ? null
                                        : contractGateResult.runtimeContract().htmlEntryPath().toString(),
                                contractGateResult.runtimeContract().runtimeOwnership() == null
                                        ? null
                                        : contractGateResult.runtimeContract().runtimeOwnership().name(),
                                contractGateResult.runtimeContract().runtimePathStrings()
                        )
        );
    }

    private devflow.agent.protocol.FileChangePayload toPayload(FileChange change) {
        if (change == null) {
            return null;
        }
        return new devflow.agent.protocol.FileChangePayload(
                change.path(),
                change.action() == null ? null : change.action().name(),
                ImplementationArtifactRenderSupport.blankIfNull(change.reason()),
                change.effectiveEditScope().name(),
                change.runtimeOwnership() == null ? null : change.runtimeOwnership().name(),
                change.hostHtmlPatchRequired()
        );
    }
}
