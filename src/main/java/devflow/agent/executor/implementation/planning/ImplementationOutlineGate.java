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

import devflow.agent.context.AuthoritativeCoverageCatalog;
import devflow.agent.context.ContractView;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.util.ArrayList;
import java.util.List;

/**
 * outline 层的确定性 gate。
 *
 * <p>它负责：
 * 1. 检查 outline 是否覆盖执行契约与权威能力引用；
 * 2. 检查子任务目标文件声明是否完整；
 * 3. 检查 runnable milestone 是否指向入口/运行表面，而不是只覆盖内部模块。
 */
final class ImplementationOutlineGate {

    private static final String OUTLINE_UNIT_ID = "outline";

    private final ImplementationPlanCoverageAnalyzer coverageAnalyzer;
    private final ImplementationPlanChangeGate changeGate;

    ImplementationOutlineGate(ImplementationPlanCoverageAnalyzer coverageAnalyzer) {
        this.coverageAnalyzer = coverageAnalyzer;
        this.changeGate = new ImplementationPlanChangeGate();
    }

    GateReport evaluate(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            QualityPlan qualityPlan,
            DeliveryPolicyEnvelope deliveryPolicy,
            PlanningRuntimeFacts runtimeFacts,
            ImplementationContinuationConstraints continuationConstraints,
            ImplementationOutline outline
    ) {
        if (outline == null || outline.subtasks() == null || outline.subtasks().isEmpty()) {
            return GateReport.failure(
                    "Implementation outline 不能为空。",
                    List.of(new GateIssue(
                            "PLAN_OUTLINE_1",
                            "Implementation outline 必须至少包含一个子任务。",
                            GateFailureDisposition.REPLAN_CURRENT_STAGE
                    ))
            );
        }
        List<GateIssue> issues = new ArrayList<>();
        issues.addAll(validateTargetPaths(deliveryPolicy, outline.subtasks()));
        issues.addAll(validateRunnableMilestones(contractView, outline.subtasks()));
        issues.addAll(changeGate.evaluateOutlineContinuationConstraints(runtimeFacts, continuationConstraints, outline.subtasks()));
        CoverageResult capabilityPartitionResult = coverageAnalyzer.analyzeCapabilityPartitionOutline(outline.subtasks());
        CoverageResult coverageResult = coverageAnalyzer.analyze(
                fingerprint,
                contractView,
                plannedPaths(outline.subtasks()),
                plannedCoverageItems(outline.subtasks()),
                plannedCoverageRefs(outline.subtasks(), contractView, qualityPlan),
                plannedDeliveryModes(outline.subtasks()),
                outline.subtasks().stream().anyMatch(ImplementationOutlineSubtask::runnableMilestone),
                outline.subtasks().stream().anyMatch(subtask ->
                        subtask.runnableMilestone() && subtask.deliveryMode() != DeliveryMode.SKELETON),
                qualityPlan
        );
        issues.addAll(toIssues("PLAN_CAPABILITY_PARTITION", capabilityPartitionResult));
        issues.addAll(toIssues("PLAN_EXECUTION_CONTRACT", coverageResult));
        if (coverageResult.passed() && capabilityPartitionResult.passed() && issues.isEmpty()) {
            return GateReport.success();
        }
        return GateReport.failure(
                mergeSummaries(
                        mergeSummaries(capabilityPartitionResult.summary(), coverageResult.summary()),
                        outlineIssueSummary(issues)
                ),
                issues
        );
    }

    String toRetryFeedback(GateReport report) {
        return report.toRetryFeedback("""
                请重新规划 implementation outline，并确保：
                1. 子任务集合覆盖执行契约要求的入口与最小可运行表面
                2. 每个子任务必须声明自己的 targetPaths
                3. runnable milestone 必须直接覆盖入口或运行表面
                4. 不要在 outline 阶段输出具体文件补丁
                %s
                """.formatted(PlanningBoundaryContractPromptSupport.outlineRetryGuidanceBlock()).trim());
    }

    private List<GateIssue> validateTargetPaths(
            DeliveryPolicyEnvelope deliveryPolicy,
            List<ImplementationOutlineSubtask> subtasks
    ) {
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        for (ImplementationOutlineSubtask subtask : subtasks) {
            if (subtask == null) {
                continue;
            }
            List<String> targetPaths = safeList(subtask.targetPaths());
            if (targetPaths.isEmpty()) {
                issues.add(new GateIssue(
                        "PLAN_OUTLINE_" + issueIndex++,
                        "Outline 子任务必须声明至少一个 targetPaths: " + blankIfNull(subtask.id()),
                        GateFailureDisposition.REPLAN_CURRENT_STAGE,
                        GateIssueContext.forPlanningUnit(
                                ImplementationPlanningUnitKind.OUTLINE,
                                OUTLINE_UNIT_ID
                        )
                ));
                continue;
            }
            if (deliveryPolicy != null && targetPaths.size() > deliveryPolicy.maxFiles()) {
                issues.add(new GateIssue(
                        "PLAN_OUTLINE_" + issueIndex++,
                        "Outline 子任务 targetPaths 不能超过 %d 个: %s".formatted(deliveryPolicy.maxFiles(), subtask.id()),
                        GateFailureDisposition.REPLAN_CURRENT_STAGE,
                        GateIssueContext.forPlanningUnitPaths(
                                ImplementationPlanningUnitKind.OUTLINE,
                                OUTLINE_UNIT_ID,
                                targetPaths
                        )
                ));
            }
        }
        return issues;
    }

    private List<GateIssue> validateRunnableMilestones(
            ContractView contractView,
            List<ImplementationOutlineSubtask> subtasks
    ) {
        if (contractView == null
                || contractView.executionContract() == null
                || !contractView.executionContract().requiresHtmlEntry()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        for (ImplementationOutlineSubtask subtask : subtasks) {
            if (subtask == null || !subtask.runnableMilestone()) {
                continue;
            }
            boolean touchesHtmlEntry = safeList(subtask.targetPaths()).stream()
                    .anyMatch(devflow.agent.util.ProjectPathSupport::isHtml);
            if (!touchesHtmlEntry) {
                issues.add(new GateIssue(
                        "PLAN_RUNNABLE_MILESTONE_" + issueIndex++,
                        "可运行里程碑子任务必须直接覆盖 HTML 入口文件，不能只改内部逻辑模块。",
                        GateFailureDisposition.REPLAN_CURRENT_STAGE,
                        GateIssueContext.forPlanningUnitPaths(
                                ImplementationPlanningUnitKind.OUTLINE,
                                OUTLINE_UNIT_ID,
                                subtask.targetPaths()
                        )
                ));
            }
        }
        return issues;
    }

    private List<String> plannedPaths(List<ImplementationOutlineSubtask> subtasks) {
        return subtasks.stream()
                .flatMap(subtask -> safeList(subtask.targetPaths()).stream())
                .toList();
    }

    private List<String> plannedCoverageItems(List<ImplementationOutlineSubtask> subtasks) {
        return subtasks.stream()
                .flatMap(subtask -> List.of(
                        blankIfNull(subtask.title()),
                        blankIfNull(subtask.goal()),
                        String.join("；", safeList(subtask.coverageRefs())),
                        String.join("；", safeList(subtask.ownedCapabilities())),
                        String.join("；", safeList(subtask.deferredCapabilities())),
                        String.join("；", safeList(subtask.acceptanceCriteria()))
                ).stream())
                .toList();
    }

    private List<String> plannedCoverageRefs(
            List<ImplementationOutlineSubtask> subtasks,
            ContractView contractView,
            QualityPlan qualityPlan
    ) {
        AuthoritativeCoverageCatalog catalog = AuthoritativeCoverageCatalog.from(
                contractView == null ? null : contractView.productContract(),
                qualityPlan
        );
        return subtasks.stream()
                .flatMap(subtask -> safeList(subtask.coverageRefs()).stream())
                .filter(value -> catalog != null && catalog.containsReferenceId(value))
                .distinct()
                .toList();
    }

    private List<String> plannedDeliveryModes(List<ImplementationOutlineSubtask> subtasks) {
        return subtasks.stream()
                .map(ImplementationOutlineSubtask::deliveryMode)
                .map(mode -> mode == null ? DeliveryMode.INCREMENTAL.name() : mode.name())
                .toList();
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

    private String outlineIssueSummary(List<GateIssue> issues) {
        return issues.stream()
                .map(GateIssue::message)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse("");
    }

    private String mergeSummaries(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null ? "" : right.trim();
        }
        if (right == null || right.isBlank() || left.contains(right)) {
            return left.trim();
        }
        return (left.trim() + " " + right.trim()).trim();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }
}
