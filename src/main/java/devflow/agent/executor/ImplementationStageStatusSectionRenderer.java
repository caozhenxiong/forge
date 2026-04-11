package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.review.ImplementationPatchTarget;

/**
 * 负责 implementation 主报告里的阶段完成状态 section。
 *
 * <p>这层只渲染 stageStatus 对外说明，不参与子任务拆解和执行结果展开。
 */
final class ImplementationStageStatusSectionRenderer {

    String render(
            ImplementationStageStatus stageStatus,
            ArchitectIntegrationCheckResult architectCheckResult,
            DocumentLanguage language
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("## ").append(language.choose("阶段完成状态", "Stage Completion")).append("\n\n");
        builder.append("- plannedSubtasks: ").append(stageStatus.plannedSubtasks()).append('\n');
        builder.append("- executedSubtasks: ").append(stageStatus.executedSubtasks()).append('\n');
        builder.append("- completedSubtasks: ").append(stageStatus.completedSubtasks()).append('\n');
        builder.append("- stageReady: ").append(stageStatus.stageReady()).append('\n');
        builder.append("- planCompleted: ").append(stageStatus.planCompleted()).append('\n');
        builder.append("- architectCheckPassed: ").append(stageStatus.architectCheckPassed()).append('\n');
        builder.append("- continuationMode: ").append(stageStatus.continuationMode()).append('\n');
        if (architectCheckResult != null && !architectCheckResult.passed()) {
            builder.append("- architectFailureReason: ").append(architectCheckResult.failureReason()).append('\n');
            builder.append("- architectFailureDetails: ").append(architectCheckResult.details()).append('\n');
            if (architectCheckResult.implementationPatchTarget() != null
                    && architectCheckResult.implementationPatchTarget() != ImplementationPatchTarget.NONE) {
                builder.append("- implementationPatchTarget: ")
                        .append(architectCheckResult.implementationPatchTarget())
                        .append('\n');
            }
        }
        builder.append("- incompleteSubtasks: ").append(stageStatus.incompleteSubtasks().isEmpty()
                ? PlaceholderValues.machineNone()
                : String.join(language.choose("；", "; "), stageStatus.incompleteSubtasks())).append("\n\n");
        if (stageStatus.blockedForHuman()) {
            builder.append("- continuationSummary: ").append(stageStatus.continuationSummary()).append('\n');
            builder.append("- continuationReasonCode: ").append(stageStatus.continuationReasonCode()).append('\n');
            if (!stageStatus.continuationChangeRequest().isBlank()) {
                builder.append("- continuationChangeRequest: ").append(stageStatus.continuationChangeRequest()).append('\n');
            }
            if (!stageStatus.continuationEvidence().isBlank()) {
                builder.append("- continuationEvidence: ").append(stageStatus.continuationEvidence()).append('\n');
            }
            builder.append('\n');
        }
        return builder.toString();
    }
}
