package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 负责按顺序执行 implementation plan 中的子任务。
 *
 * <p>这层只关心子任务的执行顺序、共享反馈如何滚动、以及何时发布中间进度，
 * 不负责 planning、阶段级 gate 或最终 artifact 渲染。
 */
class ImplementationPlanRunner {

    @FunctionalInterface
    interface ProgressPublisher {
        void publish(List<SubtaskExecutionReport> reports, String currentSubtaskTitle);
    }

    private final SubtaskExecutor subtaskExecutor;

    ImplementationPlanRunner(SubtaskExecutor subtaskExecutor) {
        this.subtaskExecutor = subtaskExecutor;
    }

    List<SubtaskExecutionReport> execute(
            Path projectPath,
            RunRecord runRecord,
            String note,
            ImplementationPlan plan,
            List<TaskPackage> taskPackages,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            DocumentLanguage language,
            List<SubtaskExecutionReport> completedPrefix,
            SubtaskExecutionState resumedExecutionState,
            SharedContextBundle sharedContextBundle,
            String coderContextMarkdown,
            ProgressPublisher progressPublisher,
            ImplementationEventJournal eventJournal
    ) {
        List<SubtaskExecutionReport> reports = new ArrayList<>();
        if (completedPrefix != null && !completedPrefix.isEmpty()) {
            reports.addAll(completedPrefix);
        }
        String persistentRepairFeedback = repairFeedback(note);
        String sharedFeedback = mergeFeedback(persistentRepairFeedback, note == null ? "" : note);
        SubtaskExecutionState initialExecutionState = resumedExecutionState == null ? null : resumedExecutionState.copy();
        for (int index = reports.size(); index < plan.subtasks().size(); index++) {
            Subtask subtask = plan.subtasks().get(index);
            TaskPackage taskPackage = index < taskPackages.size() ? taskPackages.get(index) : null;
            publishEvent(
                    eventJournal,
                    ImplementationEventMessages.subtaskStart(
                            subtask.title(),
                            subtask.deliveryMode(),
                            subtask.changes().stream().map(FileChange::path).collect(Collectors.joining(","))
                    )
            );
            publishProgress(progressPublisher, reports, subtask.title());
            SubtaskExecutionReport report = subtaskExecutor.executeSubtask(
                    projectPath,
                    runRecord,
                    plan.summary(),
                    subtask,
                    taskPackage,
                    sharedFeedback,
                    persistentRepairFeedback,
                    deliveryPolicy,
                    contractView,
                    qualityPlan,
                    fingerprint,
                    index == plan.subtasks().size() - 1,
                    language,
                    coderContextMarkdown,
                    eventJournal,
                    initialExecutionState
            );
            initialExecutionState = null;
            reports.add(report);
            publishEvent(
                    eventJournal,
                    ImplementationEventMessages.subtaskFinished(subtask.title(), report.completed(), report.attempts().size())
            );
            publishProgress(progressPublisher, reports, null);
            if (!report.completed()) {
                break;
            }
            sharedFeedback = mergeFeedback(persistentRepairFeedback, report.lastVerifierChangeRequest());
        }
        return reports;
    }

    private void publishProgress(ProgressPublisher progressPublisher, List<SubtaskExecutionReport> reports, String currentSubtaskTitle) {
        if (progressPublisher == null) {
            return;
        }
        progressPublisher.publish(List.copyOf(reports), currentSubtaskTitle);
    }

    private void publishEvent(ImplementationEventJournal eventJournal, String event) {
        if (eventJournal == null || event == null || event.isBlank()) {
            return;
        }
        eventJournal.append(event);
    }

    private String repairFeedback(String note) {
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(note);
        if (!Boolean.TRUE.equals(directives.repairBriefEnforced())) {
            return "";
        }
        return note;
    }

    private String mergeFeedback(String inheritedFeedback, String newFeedback) {
        String inherited = inheritedFeedback == null ? "" : inheritedFeedback.trim();
        String fresh = newFeedback == null ? "" : newFeedback.trim();
        if (inherited.isBlank()) {
            return fresh;
        }
        if (fresh.isBlank()) {
            return inherited;
        }
        return inherited + "\n\n" + fresh;
    }
}
