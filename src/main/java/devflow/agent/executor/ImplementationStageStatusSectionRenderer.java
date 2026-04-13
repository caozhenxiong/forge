package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.review.ImplementationPatchTarget;

/**
 * 负责 implementation 主报告里的阶段完成状态 section。
 *
 * <p>这层只渲染 stageStatus 对外说明，不参与子任务拆解和执行结果展开。
 */
final class ImplementationStageStatusSectionRenderer {

    String render(ImplementationStageStatus stageStatus, DocumentLanguage language) {
        StringBuilder builder = new StringBuilder();
        ArchitectIntegrationCheckResult contractGateResult = stageStatus.contractGateResult();
        builder.append("## ").append(language.choose("阶段完成状态", "Stage Completion")).append("\n\n");
        builder.append("- plannedSubtasks: ").append(stageStatus.plannedSubtasks()).append('\n');
        builder.append("- executedSubtasks: ").append(stageStatus.executedSubtasks()).append('\n');
        builder.append("- completedSubtasks: ").append(stageStatus.completedSubtasks()).append('\n');
        builder.append("- stageReady: ").append(stageStatus.stageReady()).append('\n');
        builder.append("- planCompleted: ").append(stageStatus.planCompleted()).append('\n');
        builder.append("- contractGatePassed: ").append(stageStatus.contractGatePassed()).append('\n');
        builder.append("- continuationMode: ").append(stageStatus.continuationMode()).append('\n');
        if (contractGateResult != null) {
            builder.append("- contractGateScope: ").append(contractGateResult.scope()).append('\n');
            if (!contractGateResult.passed()) {
                builder.append("- contractFailureReason: ").append(contractGateResult.failureReason()).append('\n');
                builder.append("- contractFailureDetails: ").append(contractGateResult.details()).append('\n');
                if (contractGateResult.implementationPatchTarget() != null
                        && contractGateResult.implementationPatchTarget() != ImplementationPatchTarget.NONE) {
                    builder.append("- contractPatchTarget: ")
                            .append(contractGateResult.implementationPatchTarget())
                            .append('\n');
                }
            }
        }
        builder.append("- incompleteSubtasks: ").append(stageStatus.incompleteSubtasks().isEmpty()
                ? PlaceholderValues.machineNone()
                : String.join(language.choose("；", "; "), stageStatus.incompleteSubtasks())).append("\n\n");
        if (stageStatus.hasContinuationDirective()) {
            builder.append("- continuationSummary: ").append(stageStatus.continuationSummary()).append('\n');
            if (stageStatus.continuationPatchTarget().concretePatch()) {
                builder.append("- continuationPatchTarget: ").append(stageStatus.continuationPatchTarget()).append('\n');
            }
            if (stageStatus.continuationReasonCode() != devflow.agent.review.ReviewReasonCode.NONE) {
                builder.append("- continuationReasonCode: ").append(stageStatus.continuationReasonCode()).append('\n');
            }
            if (!stageStatus.continuationChangeRequest().isBlank()) {
                builder.append("- continuationChangeRequest: ").append(stageStatus.continuationChangeRequest()).append('\n');
            }
            if (!stageStatus.continuationEvidence().isBlank()) {
                builder.append("- continuationEvidence: ").append(stageStatus.continuationEvidence()).append('\n');
            }
            if (!stageStatus.continuationActionItems().isBlank()) {
                builder.append("- continuationActionItems: ").append(stageStatus.continuationActionItems()).append('\n');
            }
            if (!stageStatus.continuationOverrideChanges().isEmpty()) {
                builder.append("- continuationOverrideChanges: ")
                        .append(ImplementationArtifactRenderSupport.renderChangeList(stageStatus.continuationOverrideChanges()))
                        .append('\n');
            }
            builder.append('\n');
        }
        return builder.toString();
    }
}
