package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

/**
 * 负责 implementation_progress.md 的实时视图渲染。
 *
 * <p>这里专注“当前跑到哪一步”，不负责事件明细和 JSON 状态投影，
 * 避免运行态渲染再次长成一个大而全的类。
 */
final class ImplementationProgressRenderer {

    String renderProgress(ImplementationRuntimeSnapshot runtimeSnapshot) {
        ImplementationPlan plan = runtimeSnapshot.plan();
        List<SubtaskExecutionReport> reports = runtimeSnapshot.reports();
        String currentSubtaskTitle = runtimeSnapshot.currentSubtaskTitle();
        DocumentLanguage language = runtimeSnapshot.language();
        ImplementationStageStatus stageStatus = runtimeSnapshot.stageStatus();
        boolean architectCheckPassed = runtimeSnapshot.architectCheckResult() != null
                ? runtimeSnapshot.architectCheckResult().passed()
                : stageStatus != null && stageStatus.architectCheckPassed();
        StringBuilder builder = new StringBuilder("# ")
                .append(language.choose("实现进度", "Implementation Progress"))
                .append("\n\n");
        builder.append("- plannedSubtasks: ").append(stageStatus.plannedSubtasks()).append('\n');
        builder.append("- executedSubtasks: ").append(stageStatus.executedSubtasks()).append('\n');
        builder.append("- completedSubtasks: ").append(stageStatus.completedSubtasks()).append('\n');
        builder.append("- currentSubtask: ")
                .append(ImplementationArtifactRenderSupport.blankIfNull(currentSubtaskTitle).isBlank() ? PlaceholderValues.machineNone() : currentSubtaskTitle)
                .append('\n');
        builder.append("- stageReady: ").append(stageStatus.stageReady()).append('\n');
        builder.append("- planCompleted: ").append(stageStatus.planCompleted()).append('\n');
        builder.append("- architectCheckPassed: ").append(architectCheckPassed).append('\n');
        builder.append("- continuationMode: ").append(stageStatus.continuationMode()).append('\n');
        if (runtimeSnapshot.architectCheckResult() != null && !runtimeSnapshot.architectCheckResult().passed()) {
            builder.append("- architectFailureReason: ").append(runtimeSnapshot.architectCheckResult().failureReason()).append('\n');
            builder.append("- architectFailureDetails: ").append(runtimeSnapshot.architectCheckResult().details()).append('\n');
        }
        if (stageStatus.blockedForHuman()) {
            builder.append("- continuationSummary: ").append(stageStatus.continuationSummary()).append('\n');
            if (!stageStatus.continuationEvidence().isBlank()) {
                builder.append("- continuationEvidence: ").append(stageStatus.continuationEvidence()).append('\n');
            }
        }
        builder.append("\n");
        builder.append("## ").append(language.choose("子任务状态", "Subtask Status")).append("\n\n");
        if (plan == null || plan.subtasks() == null || plan.subtasks().isEmpty()) {
            builder.append(PlaceholderValues.bulletNone(language)).append('\n');
            return builder.toString().trim();
        }
        for (int index = 0; index < plan.subtasks().size(); index++) {
            Subtask subtask = plan.subtasks().get(index);
            SubtaskExecutionReport report = index < reports.size() ? reports.get(index) : null;
            builder.append("### ").append(index + 1).append(". ").append(subtask.title()).append("\n\n");
            builder.append("- ").append(language.choose("状态", "Status")).append(": ")
                    .append(resolveProgressStatus(subtask, report, currentSubtaskTitle))
                    .append('\n');
            builder.append("- ").append(language.choose("目标", "Goal")).append(": ").append(subtask.goal()).append('\n');
            builder.append("- ").append(language.choose("交付模式", "Delivery Mode")).append(": ").append(subtask.deliveryMode()).append('\n');
            builder.append("- ").append(language.choose("文件", "Files")).append(": ")
                    .append(ImplementationArtifactRenderSupport.renderChangeList(
                            report == null ? subtask.changes() : report.effectiveChanges()
                    ))
                    .append('\n');
            if (report != null && !report.attempts().isEmpty()) {
                SubtaskAttemptReport latest = report.attempts().get(report.attempts().size() - 1);
                builder.append("- ").append(language.choose("最新验证", "Latest Review")).append(": ")
                        .append(latest.review().decision()).append(" / ")
                        .append(ImplementationArtifactRenderSupport.blankIfNull(latest.review().summary())).append('\n');
                if (!ImplementationArtifactRenderSupport.blankIfNull(latest.review().changeRequest()).isBlank()) {
                    builder.append("- ").append(language.choose("最新修改要求", "Latest Change Request")).append(": ")
                            .append(latest.review().changeRequest())
                            .append('\n');
                }
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String resolveProgressStatus(
            Subtask subtask,
            SubtaskExecutionReport report,
            String currentSubtaskTitle
    ) {
        if (report != null) {
            return report.completed() ? "COMPLETED" : "FAILED";
        }
        if (subtask != null && subtask.title().equals(currentSubtaskTitle)) {
            return "RUNNING";
        }
        return "PENDING";
    }
}
