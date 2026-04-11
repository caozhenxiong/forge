package devflow.agent.executor;

import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一处理 implementation 阶段的整体 gate。
 *
 * <p>它负责把“计划执行情况”和“architect 级整体检查”收敛成一份稳定结果，
 * 避免 ImplementationExecutor 一边执行子任务，一边再手工拼阶段收尾逻辑。
 */
class ImplementationGateEngine {

    private final ImplementationStageGate implementationStageGate;
    private final ArchitectIntegrationCheck architectIntegrationCheck;

    ImplementationGateEngine(
            ImplementationStageGate implementationStageGate,
            ArchitectIntegrationCheck architectIntegrationCheck
    ) {
        this.implementationStageGate = implementationStageGate;
        this.architectIntegrationCheck = architectIntegrationCheck;
    }

    /**
     * 汇总 implementation 阶段的最终状态。
     *
     * <p>如果计划尚未完成，只返回当前执行状态；
     * 如果计划已完成，则进一步执行 architect 级整体检查，并在失败时追加统一的阶段级失败报告。
     */
    ImplementationGateOutcome evaluate(
            Path projectPath,
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            ExecutionContract executionContract,
            DocumentLanguage language
    ) {
        ImplementationStageStatus stageStatus = implementationStageGate.summarizeStageStatus(plan, reports, true);
        if (stageStatus.blockedForHuman()) {
            return new ImplementationGateOutcome(reports, stageStatus, null);
        }
        if (!stageStatus.planCompleted()) {
            return new ImplementationGateOutcome(reports, stageStatus, null);
        }

        ArchitectIntegrationCheckResult architectCheckResult = architectIntegrationCheck.verify(projectPath, executionContract);
        if (architectCheckResult.passed()) {
            return new ImplementationGateOutcome(
                    reports,
                    implementationStageGate.summarizeStageStatus(plan, reports, true),
                    architectCheckResult
            );
        }

        List<SubtaskExecutionReport> extendedReports =
                implementationStageGate.appendArchitectCheckFailure(reports, architectCheckResult, language);
        return new ImplementationGateOutcome(
                extendedReports,
                implementationStageGate.summarizeStageStatus(plan, extendedReports, false),
                architectCheckResult
        );
    }
}
