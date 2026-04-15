package devflow.agent.executor.implementation;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.protocol.ExecutionDirectiveFeedbackSupport;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.implementation.ImplementationEventMessages;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionContext;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.SubtaskExecutor;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 负责按顺序执行 implementation plan 中的子任务。
 *
 * <p>这层只关心子任务的执行顺序、共享反馈如何滚动、以及何时发布中间进度，
 * 不负责 planning、阶段级 gate 或最终 artifact 渲染。
 */
public class ImplementationPlanRunner {

    @FunctionalInterface
    interface ProgressPublisher {
        void publish(List<SubtaskExecutionReport> reports, String currentSubtaskTitle);
    }

    private final SubtaskExecutor subtaskExecutor;

    public ImplementationPlanRunner(SubtaskExecutor subtaskExecutor) {
        this.subtaskExecutor = subtaskExecutor;
    }

    public List<SubtaskExecutionReport> execute(
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
        String persistentRepairBriefFeedback = repairBriefFeedback(note);
        String persistentRetryFeedback = repairFeedback(note);
        String sharedFeedback = mergeFeedback(persistentRetryFeedback, note == null ? "" : note);
        SubtaskExecutionState initialExecutionState = resumedExecutionState == null ? null : resumedExecutionState.copy();
        boolean firstActiveSubtask = true;
        for (int index = reports.size(); index < plan.subtasks().size(); index++) {
            Subtask subtask = plan.subtasks().get(index);
            TaskPackage taskPackage = index < taskPackages.size() ? taskPackages.get(index) : null;
            String subtaskPersistentFeedback = firstActiveSubtask
                    ? persistentRetryFeedback
                    : persistentRepairBriefFeedback;
            publishEvent(
                    eventJournal,
                    ImplementationEventMessages.subtaskStart(
                            subtask.title(),
                            subtask.deliveryMode(),
                            subtask.changes().stream().map(FileChange::path).collect(Collectors.joining(","))
                    )
            );
            publishProgress(progressPublisher, reports, subtask.title());
            SubtaskExecutionReport report = subtaskExecutor.executeSubtask(new SubtaskExecutionContext(
                    projectPath,
                    runRecord,
                    plan.summary(),
                    subtask,
                    taskPackage,
                    sharedFeedback,
                    subtaskPersistentFeedback,
                    deliveryPolicy,
                    contractView,
                    qualityPlan,
                    fingerprint,
                    index == plan.subtasks().size() - 1,
                    language,
                    coderContextMarkdown,
                    eventJournal,
                    initialExecutionState
            ));
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
            firstActiveSubtask = false;
            sharedFeedback = mergeFeedback(persistentRepairBriefFeedback, report.lastVerifierChangeRequest());
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
        return ExecutionDirectiveFeedbackSupport.persistentRetryFeedback(note);
    }

    private String repairBriefFeedback(String note) {
        return ExecutionDirectiveFeedbackSupport.repairBriefFeedback(note);
    }

    private String mergeFeedback(String inheritedFeedback, String newFeedback) {
        return ExecutionDirectiveFeedbackSupport.merge(inheritedFeedback, newFeedback);
    }
}
