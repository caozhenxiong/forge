package devflow.agent.executor;

import devflow.agent.util.ProjectPathSupport;
import java.util.ArrayList;
import java.util.List;

/**
 * implementation planning 的第一批确定性 gate。
 *
 * <p>当前只统一两类最稳定的检查：
 * 1. Execution Contract / 产品能力 coverage
 * 2. runnable milestone 结构自洽
 *
 * <p>这样可以先把 planning 阶段最核心的“能否进入执行”判断从 planner 本体里拿出来，
 * 为后续 GateEngine 扩展打基础。
 */
class ImplementationPlanGate implements DeterministicGate<ImplementationPlanGateInput> {

    private static final String PLANNING_RETRY_GUIDANCE = """
            请重新规划子任务，并确保：
            1. 子任务集合覆盖执行契约要求的入口与最小可运行表面
            2. 不要只拆内部逻辑模块
            3. 保持小步交付，但第一批交付必须形成可运行表面
            4. 至少有一个子任务必须负责把当前交付物接成可启动、可验证的运行状态
            """;

    private final ImplementationPlanCoverageAnalyzer coverageAnalyzer;

    ImplementationPlanGate(ImplementationPlanCoverageAnalyzer coverageAnalyzer) {
        this.coverageAnalyzer = coverageAnalyzer;
    }

    @Override
    public GateReport evaluate(ImplementationPlanGateInput input) {
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
        issues.addAll(toIssues("PLAN_EXECUTION_CONTRACT", coverageResult));
        issues.addAll(toIssues("PLAN_RUNNABLE_MILESTONE", runnableMilestoneResult));
        issues.addAll(evaluateRuntimeOwnership(input));
        issues.addAll(evaluateContinuationConstraints(input));
        if (coverageResult.passed() && runnableMilestoneResult.passed() && issues.isEmpty()) {
            return GateReport.success();
        }

        return GateReport.failure(
                mergeSummaries(
                        coverageResult,
                        runnableMilestoneResult,
                        summarizeContinuationIssues(issues)
                ),
                issues
        );
    }

    String toPlanningFeedback(GateReport report) {
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
                    GateFailureDisposition.REPLAN_CURRENT_STAGE
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

    private CoverageResult summarizeContinuationIssues(List<GateIssue> issues) {
        List<String> continuationIssues = issues.stream()
                .filter(issue -> issue != null && (issue.code().startsWith("PLAN_CONTINUATION_")
                        || issue.code().startsWith("PLAN_RUNTIME_")))
                .map(GateIssue::message)
                .toList();
        if (continuationIssues.isEmpty()) {
            return new CoverageResult(true, "", List.of());
        }
        return new CoverageResult(false, "当前 continuation 计划回退了已有文件的交付模式。", continuationIssues);
    }

    private List<GateIssue> evaluateContinuationConstraints(ImplementationPlanGateInput input) {
        ImplementationContinuationConstraints constraints = input.continuationConstraints();
        if (constraints == null || !constraints.active() || input.subtasks() == null || input.subtasks().isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        for (Subtask subtask : input.subtasks()) {
            if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
                continue;
            }
            for (FileChange change : subtask.changes()) {
                if (change == null || change.path() == null || change.path().isBlank()) {
                    continue;
                }
                if (subtask.deliveryMode() == DeliveryMode.SKELETON && constraints.marksExistingPath(change.path())) {
                    issues.add(new GateIssue(
                            "PLAN_CONTINUATION_" + issueIndex++,
                            "Continuation 计划不能把已存在文件重新规划为 SKELETON: " + change.path(),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE
                    ));
                }
                if (subtask.deliveryMode() == DeliveryMode.REWORK && constraints.protectsHtmlEntry(change.path())) {
                    issues.add(new GateIssue(
                            "PLAN_CONTINUATION_" + issueIndex++,
                            "Continuation 计划不能对现有 HTML 入口使用 REWORK/整页重写: " + change.path(),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE
                    ));
                }
                RuntimeOwnershipMode protectedRuntimeOwnership = constraints.protectedRuntimeOwnership(change.path());
                if (protectedRuntimeOwnership != null
                        && ProjectPathSupport.isHtml(change.path())
                        && change.runtimeOwnership() != null
                        && change.runtimeOwnership() != protectedRuntimeOwnership) {
                    issues.add(new GateIssue(
                            "PLAN_CONTINUATION_" + issueIndex++,
                            "Continuation 计划不能切换现有 HTML 入口的 runtimeOwnership: %s -> %s (%s)"
                                    .formatted(protectedRuntimeOwnership, change.runtimeOwnership(), change.path()),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE
                    ));
                }
            }
        }
        return issues;
    }

    private List<GateIssue> evaluateRuntimeOwnership(ImplementationPlanGateInput input) {
        if (input == null || input.subtasks() == null || input.subtasks().isEmpty()) {
            return List.of();
        }
        java.nio.file.Path htmlEntryPath = resolveHtmlEntryPath(input);
        if (htmlEntryPath == null) {
            return List.of();
        }
        java.nio.file.Path companionRuntimePath = devflow.agent.util.ProjectPathSupport.extractedInlineScriptAssetPath(htmlEntryPath).normalize();
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        RuntimeOwnershipMode protectedOwnership = input.continuationConstraints() == null
                ? null
                : input.continuationConstraints().protectedRuntimeOwnership(htmlEntryPath.toString());
        RuntimeOwnershipMode planOwnership = protectedOwnership;
        boolean companionTouched = false;
        for (Subtask subtask : input.subtasks()) {
            if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
                continue;
            }
            RuntimeOwnershipMode htmlChangeOwnership = null;
            boolean subtaskTouchesCompanion = false;
            for (FileChange change : subtask.changes()) {
                if (change == null || change.path() == null || change.path().isBlank()) {
                    continue;
                }
                java.nio.file.Path normalizedPath = java.nio.file.Path.of(change.path()).normalize();
                if (companionRuntimePath.equals(normalizedPath) && change.action() != ChangeAction.DELETE) {
                    companionTouched = true;
                    subtaskTouchesCompanion = true;
                }
                if (!htmlEntryPath.equals(normalizedPath) || change.action() == ChangeAction.DELETE) {
                    continue;
                }
                if (change.runtimeOwnership() == null) {
                    issues.add(new GateIssue(
                            "PLAN_RUNTIME_" + issueIndex++,
                            "HTML 入口变更必须显式声明 runtimeOwnership: " + change.path(),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE
                    ));
                    continue;
                }
                htmlChangeOwnership = change.runtimeOwnership();
                if (planOwnership != null && planOwnership != change.runtimeOwnership()) {
                    issues.add(new GateIssue(
                            "PLAN_RUNTIME_" + issueIndex++,
                            "同一个 HTML 入口不能在同一轮计划中切换 runtimeOwnership: %s -> %s (%s)"
                                    .formatted(planOwnership, change.runtimeOwnership(), change.path()),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE
                    ));
                }
                planOwnership = change.runtimeOwnership();
            }
            if (htmlChangeOwnership == RuntimeOwnershipMode.EXTERNAL_COMPANION && !subtaskTouchesCompanion) {
                issues.add(new GateIssue(
                        "PLAN_RUNTIME_" + issueIndex++,
                        "EXTERNAL_COMPANION 子任务必须同步声明 companion runtime 文件: %s"
                                .formatted(companionRuntimePath.toString().replace('\\', '/')),
                        GateFailureDisposition.REPLAN_CURRENT_STAGE
                ));
            }
            if (htmlChangeOwnership == RuntimeOwnershipMode.INLINE_HOST && subtaskTouchesCompanion) {
                issues.add(new GateIssue(
                        "PLAN_RUNTIME_" + issueIndex++,
                        "INLINE_HOST 子任务不能同时引入 companion runtime 文件: %s"
                                .formatted(companionRuntimePath.toString().replace('\\', '/')),
                        GateFailureDisposition.REPLAN_CURRENT_STAGE
                ));
            }
        }
        if (companionTouched && planOwnership != RuntimeOwnershipMode.EXTERNAL_COMPANION) {
            issues.add(new GateIssue(
                    "PLAN_RUNTIME_" + issueIndex++,
                    "计划涉及 companion runtime 文件，但 HTML 入口没有稳定的 EXTERNAL_COMPANION 所有权声明: "
                            + companionRuntimePath.toString().replace('\\', '/'),
                    GateFailureDisposition.REPLAN_CURRENT_STAGE
            ));
        }
        return issues;
    }

    private java.nio.file.Path resolveHtmlEntryPath(ImplementationPlanGateInput input) {
        if (input == null) {
            return null;
        }
        devflow.agent.context.ContractView contractView = input.contractView();
        if (contractView == null
                || contractView.executionContract() == null
                || !contractView.executionContract().requiresHtmlEntry()) {
            return null;
        }
        if (input.fingerprint() != null && input.fingerprint().hasResolvedHtmlEntry()) {
            return java.nio.file.Path.of(input.fingerprint().resolvedHtmlEntryPath()).normalize();
        }
        for (Subtask subtask : input.subtasks()) {
            if (subtask == null || subtask.changes() == null) {
                continue;
            }
            for (FileChange change : subtask.changes()) {
                if (change != null && change.path() != null && ProjectPathSupport.isHtml(change.path())) {
                    return java.nio.file.Path.of(change.path()).normalize();
                }
            }
        }
        return null;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
