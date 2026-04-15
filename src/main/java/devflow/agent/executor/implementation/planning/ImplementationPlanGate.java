package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;

import devflow.agent.executor.runtime.*;

import java.util.ArrayList;
import java.util.List;

/**
 * implementation 最终计划的确定性 gate。
 *
 * <p>它只消费本地 assemble 出来的最终 {@link ImplementationPlan}，
 * 统一检查：
 * 1. Execution Contract / 产品能力 coverage；
 * 2. runnable milestone 结构自洽；
 * 3. runtime / continuation 变更声明是否自洽。
 */
public class ImplementationPlanGate implements DeterministicGate<ImplementationPlanGateInput> {

    private static final String OUTLINE_UNIT_ID = "outline";

    private static final String PLANNING_RETRY_GUIDANCE = """
            请重新规划子任务，并确保：
            1. 子任务集合覆盖执行契约要求的入口与最小可运行表面
            2. 不要只拆内部逻辑模块
            3. 保持小步交付，但第一批交付必须形成可运行表面
            4. 至少有一个子任务必须负责把当前交付物接成可启动、可验证的运行状态
            """;

    private final ImplementationPlanCoverageAnalyzer coverageAnalyzer;
    private final ImplementationPlanChangeGate changeGate;

    public ImplementationPlanGate(ImplementationPlanCoverageAnalyzer coverageAnalyzer) {
        this.coverageAnalyzer = coverageAnalyzer;
        this.changeGate = new ImplementationPlanChangeGate();
    }

    @Override
    public GateReport evaluate(ImplementationPlanGateInput input) {
        CoverageResult capabilityPartitionResult = coverageAnalyzer.analyzeCapabilityPartition(input.subtasks());
        CoverageResult coverageResult = coverageAnalyzer.analyze(
                input.fingerprint(),
                input.contractView(),
                input.plannedPaths(),
                input.plannedCoverageItems(),
                input.plannedCoverageRefs(),
                input.plannedDeliveryModes(),
                input.hasRunnableMilestone(),
                input.hasNonSkeletonRunnableMilestone(),
                input.qualityPlan()
        );
        CoverageResult runnableMilestoneResult =
                coverageAnalyzer.analyzeRunnableMilestones(input.contractView(), input.subtasks());
        List<GateIssue> issues = new ArrayList<>();
        issues.addAll(toIssues("PLAN_CAPABILITY_PARTITION", capabilityPartitionResult));
        issues.addAll(toIssues("PLAN_EXECUTION_CONTRACT", coverageResult));
        issues.addAll(toIssues("PLAN_RUNNABLE_MILESTONE", runnableMilestoneResult));
        issues.addAll(changeGate.evaluate(
                input.runtimeFacts(),
                input.continuationConstraints(),
                input.subtasks()
        ));
        if (coverageResult.passed() && capabilityPartitionResult.passed() && runnableMilestoneResult.passed() && issues.isEmpty()) {
            return GateReport.success();
        }

        return GateReport.failure(
                mergeSummaries(
                        capabilityPartitionResult,
                        coverageResult,
                        runnableMilestoneResult,
                        summarizeChangeIssues(issues)
                ),
                issues
        );
    }

    public String toPlanningFeedback(GateReport report) {
        return report.toRetryFeedback(PLANNING_RETRY_GUIDANCE);
    }

    private List<GateIssue> toIssues(String codePrefix, CoverageResult result) {
        if (result == null || result.passed() || result.issues() == null || result.issues().isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        for (int index = 0; index < result.issues().size(); index++) {
            String message = result.issues().get(index);
            if (message == null || message.isBlank()) {
                continue;
            }
            issues.add(new GateIssue(
                    codePrefix + "_" + (index + 1),
                    message,
                    GateFailureDisposition.REPLAN_CURRENT_STAGE,
                    GateIssueContext.forPlanningUnit(
                            ImplementationPlanningUnitKind.OUTLINE,
                            OUTLINE_UNIT_ID
                    )
            ));
        }
        return issues;
    }

    private String mergeSummaries(CoverageResult... results) {
        List<String> parts = new ArrayList<>();
        for (CoverageResult result : results) {
            String value = result == null ? "" : blankIfNull(result.summary()).trim();
            if (!value.isBlank() && !parts.contains(value)) {
                parts.add(value);
            }
        }
        return String.join(" ", parts).trim();
    }

    private CoverageResult summarizeChangeIssues(List<GateIssue> issues) {
        List<String> changeIssues = issues.stream()
                .filter(issue -> issue != null && (issue.code().startsWith("PLAN_CONTINUATION_")
                        || issue.code().startsWith("PLAN_RUNTIME_")))
                .map(GateIssue::message)
                .toList();
        if (changeIssues.isEmpty()) {
            return new CoverageResult(true, "", List.of());
        }
        return new CoverageResult(false, "当前 implementation plan 的变更声明未通过结构校验。", changeIssues);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
