package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolResult;

import devflow.agent.i18n.DocumentLanguage;
import java.util.List;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskAttemptReport;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
/**
 * 负责 implementation 主报告里的子任务执行结果 section。
 *
 * <p>这里专注运行态 attempt 展开，不参与 plan 静态拆解和 repair alignment。
 */
final class ImplementationSubtaskReportRenderer {

    String render(
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            String currentSubtaskTitle,
            DocumentLanguage language
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("## ").append(language.choose("子任务执行结果", "Subtask Results")).append("\n\n");
        if (reports.isEmpty()) {
            builder.append(language.choose("- 未执行任何子任务\n", "- No subtasks executed\n"));
            return builder.toString();
        }

        int plannedCount = plan.subtasks().size();
        int renderedPlannedReports = 0;
        for (int index = 0; index < plannedCount; index++) {
            Subtask subtask = plan.subtasks().get(index);
            SubtaskExecutionReport report = index < reports.size() ? reports.get(index) : null;
            builder.append("### ").append(subtask.title()).append("\n\n");
            if (report == null || !report.subtask().equals(subtask)) {
                appendPendingOrRunning(builder, subtask, currentSubtaskTitle, language);
                continue;
            }
            renderedPlannedReports++;
            appendSubtaskReport(builder, report, language);
        }
        for (int index = renderedPlannedReports; index < reports.size(); index++) {
            SubtaskExecutionReport report = reports.get(index);
            if (report == null) {
                continue;
            }
            builder.append("### ").append(report.subtask().title()).append("\n\n");
            appendSubtaskReport(builder, report, language);
        }
        return builder.toString();
    }

    private void appendPendingOrRunning(
            StringBuilder builder,
            Subtask subtask,
            String currentSubtaskTitle,
            DocumentLanguage language
    ) {
        boolean running = subtask.title().equals(currentSubtaskTitle);
        builder.append("- ").append(language.choose("最终状态", "Final Status"))
                .append("：")
                .append(running ? "RUNNING" : "NOT_EXECUTED")
                .append('\n');
        builder.append("- ").append(language.choose("原因", "Reason")).append("：")
                .append(running
                        ? language.choose("当前子任务正在执行中，最终验证结果尚未写回。", "The current subtask is still running and its final verification result has not been written back yet.")
                        : language.choose("当前实现计划尚未执行到该子任务。", "The current implementation attempt did not reach this subtask."))
                .append("\n\n");
    }

    private void appendSubtaskReport(StringBuilder builder, SubtaskExecutionReport report, DocumentLanguage language) {
        builder.append("- ").append(language.choose("最终状态", "Final Status")).append("：").append(report.completed() ? "COMPLETED" : "FAILED").append('\n');
        for (SubtaskAttemptReport attempt : report.attempts()) {
            builder.append("- attempt=").append(attempt.attempt())
                    .append(" selfCheck=").append(attempt.selfCheck().passed() ? "PASS" : "FAIL")
                    .append(" verifier=").append(attempt.review().decision())
                    .append(" summary=").append(attempt.review().summary())
                    .append('\n');
            if (attempt.selfCheckToolResults() != null && !attempt.selfCheckToolResults().isEmpty()) {
                for (ToolResult toolResult : attempt.selfCheckToolResults()) {
                    builder.append("  - selfCheckTool: ")
                            .append(toolResult.toolName())
                            .append(" / ")
                            .append(toolResult.status())
                            .append(" / ")
                            .append(toolResult.failureCode())
                            .append(" / ")
                            .append(ImplementationArtifactRenderSupport.blankIfNull(toolResult.evidence()))
                            .append('\n');
                }
            }
            if (attempt.generationFailure() != null) {
                builder.append("  - generationFailureType: ")
                        .append(attempt.generationFailure().failureType())
                        .append('\n');
                builder.append("  - generationFailureEvidence: ")
                        .append(attempt.generationFailure().evidence())
                        .append('\n');
            }
            if (attempt.recoveryDecision() != null) {
                builder.append("  - recoveryAction: ")
                        .append(attempt.recoveryDecision().action())
                        .append(" policy=")
                        .append(attempt.recoveryDecision().deliveryPolicy().mode())
                        .append(" precise=")
                        .append(attempt.recoveryDecision().deliveryPolicy().preferPreciseEditing())
                        .append('\n');
            }
            if (!attempt.selfCheck().summary().isBlank()) {
                builder.append("  - selfCheckSummary: ").append(attempt.selfCheck().summary()).append('\n');
            }
            if (!attempt.review().changeRequest().isBlank()) {
                builder.append("  - verifierChangeRequest: ").append(attempt.review().changeRequest()).append('\n');
            }
        }
        builder.append('\n');
    }
}
