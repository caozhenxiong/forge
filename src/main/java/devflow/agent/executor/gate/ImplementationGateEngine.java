package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.implementation.planning.ImplementationPlan;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import java.nio.file.Path;
import java.util.List;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
/**
 * 统一处理 implementation 阶段的整体 gate。
 *
 * <p>它负责把“计划执行情况”和“contract gate 检查”收敛成一份稳定结果，
 * 避免 progress、report、snapshot、resume 分别再跑第二套契约判定。
 */
public class ImplementationGateEngine {

    private final ImplementationStageGate implementationStageGate;
    private final ArchitectIntegrationCheck architectIntegrationCheck;

    public ImplementationGateEngine(
            ImplementationStageGate implementationStageGate,
            ArchitectIntegrationCheck architectIntegrationCheck
    ) {
        this.implementationStageGate = implementationStageGate;
        this.architectIntegrationCheck = architectIntegrationCheck;
    }

    /**
     * 汇总 implementation 阶段的最终状态。
     */
    public ImplementationGateOutcome evaluate(
            Path projectPath,
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            ExecutionContract executionContract,
            DocumentLanguage language
    ) {
        ArchitectIntegrationCheckResult currentGate = currentContractGate(projectPath, plan, reports, executionContract);
        ImplementationStageStatus stageStatus = implementationStageGate.summarizeStageStatus(plan, reports, currentGate);
        if (stageStatus.blockedForHuman()) {
            return new ImplementationGateOutcome(reports, stageStatus, currentGate);
        }
        if (!stageStatus.planCompleted()) {
            return new ImplementationGateOutcome(reports, stageStatus, currentGate);
        }

        ArchitectIntegrationCheckResult stageGate = architectIntegrationCheck.verify(projectPath, executionContract);
        return new ImplementationGateOutcome(
                reports,
                implementationStageGate.summarizeStageStatus(plan, reports, stageGate),
                stageGate
        );
    }

    /**
     * progress/provisional snapshot 只在两种情况下记录 contract gate：
     * 1. runnable milestone 子任务刚被打回；
     * 2. 整体计划已经完成，需要 stage completion gate。
     */
    public ArchitectIntegrationCheckResult currentContractGate(
            Path projectPath,
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            ExecutionContract executionContract
    ) {
        if (executionContract == null) {
            return null;
        }
        int plannedSubtasks = plan == null || plan.subtasks() == null ? 0 : plan.subtasks().size();
        int executedSubtasks = Math.min(plannedSubtasks, reports == null ? 0 : reports.size());
        boolean planCompleted = plannedSubtasks > 0
                && executedSubtasks == plannedSubtasks
                && reports != null
                && reports.stream().limit(executedSubtasks).allMatch(report -> report != null && report.completed());
        if (planCompleted) {
            return architectIntegrationCheck.verify(projectPath, executionContract);
        }
        if (reports == null || reports.isEmpty() || plan == null || plan.subtasks() == null) {
            return null;
        }
        for (int index = reports.size() - 1; index >= 0; index--) {
            if (index >= plan.subtasks().size()) {
                continue;
            }
            SubtaskExecutionReport report = reports.get(index);
            Subtask subtask = plan.subtasks().get(index);
            if (report == null || subtask == null || report.completed() || !subtask.runnableMilestone()) {
                continue;
            }
            return architectIntegrationCheck.verifyRunnableMilestone(projectPath, executionContract);
        }
        return null;
    }
}
