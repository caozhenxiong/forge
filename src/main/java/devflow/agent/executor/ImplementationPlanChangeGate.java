package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.util.ProjectPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * implementation planning 中与具体文件变更声明相关的确定性 gate。
 *
 * <p>这里负责两类局部结构检查：
 * 1. continuation 不能回退现有文件的交付模式或 runtime ownership；
 * 2. HTML/runtime wiring 相关字段必须自洽。
 *
 * <p>它不负责 coverage 和 runnable milestone，那些属于 outline/final plan 的职责。
 */
final class ImplementationPlanChangeGate {

    List<GateIssue> evaluate(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            ImplementationContinuationConstraints continuationConstraints,
            List<Subtask> subtasks
    ) {
        if (subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        issues.addAll(evaluateContinuationConstraints(continuationConstraints, subtasks));
        issues.addAll(evaluateRuntimeOwnership(fingerprint, contractView, continuationConstraints, subtasks));
        return issues;
    }

    private List<GateIssue> evaluateContinuationConstraints(
            ImplementationContinuationConstraints constraints,
            List<Subtask> subtasks
    ) {
        if (constraints == null || !constraints.active()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        for (Subtask subtask : subtasks) {
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
                            GateFailureDisposition.REPLAN_CURRENT_STAGE,
                            GateIssueContext.forPath(change.path())
                    ));
                }
                if (subtask.deliveryMode() == DeliveryMode.REWORK && constraints.protectsHtmlEntry(change.path())) {
                    issues.add(new GateIssue(
                            "PLAN_CONTINUATION_" + issueIndex++,
                            "Continuation 计划不能对现有 HTML 入口使用 REWORK/整页重写: " + change.path(),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE,
                            GateIssueContext.forPath(change.path())
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
                            GateFailureDisposition.REPLAN_CURRENT_STAGE,
                            GateIssueContext.forPath(change.path())
                    ));
                }
            }
        }
        return issues;
    }

    private List<GateIssue> evaluateRuntimeOwnership(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            ImplementationContinuationConstraints continuationConstraints,
            List<Subtask> subtasks
    ) {
        Path htmlEntryPath = resolveHtmlEntryPath(fingerprint, contractView, subtasks);
        if (htmlEntryPath == null) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        HtmlRuntimeOwnershipContract protectedContract = continuationConstraints == null
                ? null
                : continuationConstraints.protectedRuntimeContract(htmlEntryPath.toString());
        RuntimeOwnershipMode planOwnership = protectedContract == null ? null : protectedContract.runtimeOwnership();
        List<Path> touchedRuntimeScripts = new ArrayList<>();
        for (Subtask subtask : subtasks) {
            if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
                continue;
            }
            RuntimeOwnershipMode htmlChangeOwnership = null;
            List<Path> runtimeScriptChanges = collectRuntimeScriptChanges(htmlEntryPath, subtask);
            touchedRuntimeScripts.addAll(runtimeScriptChanges);
            for (FileChange change : subtask.changes()) {
                if (change == null || change.path() == null || change.path().isBlank()) {
                    continue;
                }
                Path normalizedPath = Path.of(change.path()).normalize();
                if (!htmlEntryPath.equals(normalizedPath) || change.action() == ChangeAction.DELETE) {
                    continue;
                }
                if (change.effectiveEditScope() == FileEditScope.AUTO) {
                    issues.add(new GateIssue(
                            "PLAN_RUNTIME_" + issueIndex++,
                            "HTML 入口变更不能使用 AUTO editScope，必须显式声明宿主或内联作用域: " + change.path(),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE,
                            GateIssueContext.forPath(change.path())
                    ));
                }
                if (change.effectiveEditScope() == FileEditScope.HOST_HTML_PATCH && !change.hostHtmlPatchRequired()) {
                    issues.add(new GateIssue(
                            "PLAN_RUNTIME_" + issueIndex++,
                            "HOST_HTML_PATCH 必须同时声明 hostHtmlPatchRequired=true: " + change.path(),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE,
                            GateIssueContext.forPath(change.path())
                    ));
                }
                if ((change.effectiveEditScope() == FileEditScope.INLINE_SCRIPT_PATCH
                        || change.effectiveEditScope() == FileEditScope.INLINE_STYLE_PATCH)
                        && change.hostHtmlPatchRequired()) {
                    issues.add(new GateIssue(
                            "PLAN_RUNTIME_" + issueIndex++,
                            "INLINE_*_PATCH 不能同时声明 hostHtmlPatchRequired=true: " + change.path(),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE,
                            GateIssueContext.forPath(change.path())
                    ));
                }
                if (change.runtimeOwnership() == null) {
                    issues.add(new GateIssue(
                            "PLAN_RUNTIME_" + issueIndex++,
                            "HTML 入口变更必须显式声明 runtimeOwnership: " + change.path(),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE,
                            GateIssueContext.forPath(change.path())
                    ));
                    continue;
                }
                htmlChangeOwnership = change.runtimeOwnership();
                if (planOwnership != null && planOwnership != change.runtimeOwnership()) {
                    issues.add(new GateIssue(
                            "PLAN_RUNTIME_" + issueIndex++,
                            "同一个 HTML 入口不能在同一轮计划中切换 runtimeOwnership: %s -> %s (%s)"
                                    .formatted(planOwnership, change.runtimeOwnership(), change.path()),
                            GateFailureDisposition.REPLAN_CURRENT_STAGE,
                            GateIssueContext.forPath(change.path())
                    ));
                }
                planOwnership = change.runtimeOwnership();
            }
            if (htmlChangeOwnership == RuntimeOwnershipMode.EXTERNAL_COMPANION
                    && (protectedContract == null || !protectedContract.externalCompanion())
                    && runtimeScriptChanges.isEmpty()) {
                issues.add(new GateIssue(
                        "PLAN_RUNTIME_" + issueIndex++,
                        "EXTERNAL_COMPANION 子任务必须同步声明 external runtime root 文件，不能只改宿主 HTML。",
                        GateFailureDisposition.REPLAN_CURRENT_STAGE,
                        GateIssueContext.forPath(htmlEntryPath.toString())
                ));
            }
        }
        if (protectedContract != null && protectedContract.externalCompanion() && touchedRuntimeScripts.isEmpty()) {
            return issues;
        }
        if (planOwnership == RuntimeOwnershipMode.EXTERNAL_COMPANION && touchedRuntimeScripts.isEmpty()) {
            issues.add(new GateIssue(
                    "PLAN_RUNTIME_" + issueIndex,
                    "EXTERNAL_COMPANION 计划必须至少包含一个 external runtime root 文件。",
                    GateFailureDisposition.REPLAN_CURRENT_STAGE,
                    GateIssueContext.forPath(htmlEntryPath.toString())
            ));
        }
        return issues;
    }

    private List<Path> collectRuntimeScriptChanges(Path htmlEntryPath, Subtask subtask) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return List.of();
        }
        List<Path> runtimeScripts = new ArrayList<>();
        for (FileChange change : subtask.changes()) {
            if (change == null || change.path() == null || change.path().isBlank() || change.action() == ChangeAction.DELETE) {
                continue;
            }
            Path normalizedPath = Path.of(change.path()).normalize();
            if (!ProjectPathSupport.isRuntimeScript(normalizedPath) || !isUnderHtmlEntryTree(htmlEntryPath, normalizedPath)) {
                continue;
            }
            runtimeScripts.add(normalizedPath);
        }
        return runtimeScripts.stream().distinct().toList();
    }

    private boolean isUnderHtmlEntryTree(Path htmlEntryPath, Path candidatePath) {
        Path entryParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        Path candidateParent = candidatePath.getParent() == null ? Path.of("") : candidatePath.getParent().normalize();
        return candidateParent.equals(entryParent) || candidateParent.startsWith(entryParent);
    }

    private Path resolveHtmlEntryPath(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            List<Subtask> subtasks
    ) {
        if (contractView == null
                || contractView.executionContract() == null
                || !contractView.executionContract().requiresHtmlEntry()) {
            return null;
        }
        if (fingerprint != null && fingerprint.hasResolvedHtmlEntry()) {
            return Path.of(fingerprint.resolvedHtmlEntryPath()).normalize();
        }
        for (Subtask subtask : subtasks) {
            if (subtask == null || subtask.changes() == null) {
                continue;
            }
            for (FileChange change : subtask.changes()) {
                if (change != null && change.path() != null && ProjectPathSupport.isHtml(change.path())) {
                    return Path.of(change.path()).normalize();
                }
            }
        }
        return null;
    }
}
