package devflow.agent.executor.implementation.render;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.subtask.SubtaskAttemptReport;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
/**
 * 负责从 subtask execution report 派生 worker result。
 *
 * <p>worker result 是 implementation runtime snapshot 的投影视图，不应该和
 * 执行阶段并行维护第二份状态。这层把派生逻辑集中起来，避免快照装配器继续膨胀。
 */
public final class WorkerResultAssembler {

    public List<WorkerResult> buildWorkerResults(
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            String currentSubtaskTitle
    ) {
        List<WorkerResult> results = new ArrayList<>();
        for (SubtaskExecutionReport report : reports) {
            SubtaskAttemptReport lastAttempt = report.attempts().isEmpty() ? null : report.attempts().get(report.attempts().size() - 1);
            List<String> fulfilledAcceptance = List.of();
            List<String> residualRisks = new ArrayList<>();
            if (report.completed() && !safeList(report.subtask().acceptanceCriteria()).isEmpty()) {
                residualRisks.add("当前不再根据计划文本自动投影 fulfilledAcceptance；需要显式验证证据后才会宣称满足验收。");
            }
            if (lastAttempt != null && lastAttempt.generationFailure() != null) {
                residualRisks.add(lastAttempt.generationFailure().failureType() + ": " + blankIfNull(lastAttempt.generationFailure().summary()));
            }
            if (lastAttempt != null && !blankIfNull(lastAttempt.review().changeRequest()).isBlank()) {
                residualRisks.add(lastAttempt.review().changeRequest());
            }
            results.add(new WorkerResult(
                    report.subtask().title(),
                    report.completed() ? "COMPLETED" : "FAILED",
                    report.subtask().runnableMilestone(),
                    report.effectiveChanges().stream().map(FileChange::path).toList(),
                    safeList(report.subtask().coverageRefs()),
                    safeList(report.subtask().ownedCapabilities()),
                    safeList(report.subtask().deferredCapabilities()),
                    fulfilledAcceptance,
                    residualRisks,
                    lastAttempt == null ? "" : lastAttempt.selfCheck().summary(),
                    lastAttempt == null ? "" : lastAttempt.review().summary(),
                    lastAttempt == null ? "" : lastAttempt.review().changeRequest()
            ));
        }
        appendRunningResult(plan, reports, currentSubtaskTitle, results);
        return results;
    }

    private void appendRunningResult(
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            String currentSubtaskTitle,
            List<WorkerResult> results
    ) {
        if (plan == null || currentSubtaskTitle == null || currentSubtaskTitle.isBlank()) {
            return;
        }
        boolean alreadyReported = reports.stream().anyMatch(report -> report.subtask().title().equals(currentSubtaskTitle));
        if (alreadyReported) {
            return;
        }
        plan.subtasks().stream()
                .filter(subtask -> subtask.title().equals(currentSubtaskTitle))
                .findFirst()
                .ifPresent(subtask -> results.add(new WorkerResult(
                        subtask.title(),
                        "RUNNING",
                        subtask.runnableMilestone(),
                        subtask.changes().stream().map(FileChange::path).toList(),
                        safeList(subtask.coverageRefs()),
                        safeList(subtask.ownedCapabilities()),
                        safeList(subtask.deferredCapabilities()),
                        List.of(),
                        List.of("当前子任务仍在执行中，最终验证结果尚未写回。"),
                        "",
                        "当前子任务执行中",
                        ""
                )));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
