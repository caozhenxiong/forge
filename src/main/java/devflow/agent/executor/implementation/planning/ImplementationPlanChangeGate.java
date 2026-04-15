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

import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.subtask.Subtask;
/**
 * implementation planning 中与具体文件变更声明相关的最小 gate。
 *
 * <p>Claude 风格下，planning 不再决定 editScope / ownership / wiring 语义。
 * 这里仅保留两类稳定约束：
 * 1. continuation 不能回退既有实现形态；
 * 2. accepted package 不能把新的 runtime split 拆成“只有 runtime 文件、没有宿主入口 patch”的半成品。
 */
final class ImplementationPlanChangeGate {

    private static final String OUTLINE_UNIT_ID = "outline";

    List<GateIssue> evaluate(
            PlanningRuntimeFacts runtimeFacts,
            ImplementationContinuationConstraints continuationConstraints,
            List<ImplementationSubtaskDetail> acceptedDetails,
            List<Subtask> subtasks
    ) {
        if (subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>(evaluateContinuationConstraints(continuationConstraints, subtasks));
        issues.addAll(evaluateAcceptedPackageCompleteness(runtimeFacts, acceptedDetails));
        return List.copyOf(issues);
    }

    List<GateIssue> evaluateOutlineContinuationConstraints(
            PlanningRuntimeFacts runtimeFacts,
            ImplementationContinuationConstraints constraints,
            List<ImplementationOutlineSubtask> subtasks
    ) {
        List<GateIssue> issues = new ArrayList<>(evaluateOutlineAcceptedPackageCompleteness(runtimeFacts, subtasks));
        if (constraints == null || !constraints.active() || subtasks == null || subtasks.isEmpty()) {
            return List.copyOf(issues);
        }
        int issueIndex = 1;
        for (ImplementationOutlineSubtask subtask : subtasks) {
            if (subtask == null || subtask.targetPaths() == null || subtask.targetPaths().isEmpty()) {
                continue;
            }
            for (String path : subtask.targetPaths()) {
                issueIndex = appendContinuationIssues(
                        constraints,
                        issues,
                        issueIndex,
                        subtask.deliveryMode(),
                        path
                );
            }
        }
        return List.copyOf(issues);
    }

    List<GateIssue> evaluateDetailAcceptedPackageCompleteness(
            PlanningRuntimeFacts runtimeFacts,
            String subtaskId,
            List<ImplementationSubtaskDetailChange> changes
    ) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        ScopePaths scopePaths = ScopePaths.fromDetailChanges(changes);
        List<String> findings = scopePaths.assessStructuredRuntimePackage(runtimeFacts);
        if (findings.isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        String unitId = subtaskId == null || subtaskId.isBlank() ? OUTLINE_UNIT_ID : subtaskId;
        for (String finding : findings) {
            issues.add(new GateIssue(
                    "PLAN_RUNTIME_" + issueIndex++,
                    finding,
                    GateFailureDisposition.REPLAN_CURRENT_STAGE,
                    GateIssueContext.forPlanningUnitPaths(
                            ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                            unitId,
                            scopePaths.normalizedPathStrings()
                    )
            ));
        }
        return List.copyOf(issues);
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
                issueIndex = appendContinuationIssues(
                        constraints,
                        issues,
                        issueIndex,
                        subtask.deliveryMode(),
                        change.path()
                );
            }
        }
        return issues;
    }

    private List<GateIssue> evaluateAcceptedPackageCompleteness(
            PlanningRuntimeFacts runtimeFacts,
            List<ImplementationSubtaskDetail> acceptedDetails
    ) {
        if (acceptedDetails == null || acceptedDetails.isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        for (ImplementationSubtaskDetail detail : acceptedDetails) {
            ScopePaths scopePaths = ScopePaths.fromDetail(detail);
            List<String> findings = scopePaths.assessStructuredRuntimePackage(runtimeFacts);
            if (findings.isEmpty()) {
                continue;
            }
            String unitId = detail == null || detail.subtaskId() == null || detail.subtaskId().isBlank()
                    ? OUTLINE_UNIT_ID
                    : detail.subtaskId();
            for (String finding : findings) {
                issues.add(new GateIssue(
                        "PLAN_RUNTIME_" + issueIndex++,
                        finding,
                        GateFailureDisposition.REPLAN_CURRENT_STAGE,
                        GateIssueContext.forPlanningUnitPaths(
                                ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                                unitId,
                                scopePaths.normalizedPathStrings()
                        )
                ));
            }
        }
        return List.copyOf(issues);
    }

    private List<GateIssue> evaluateOutlineAcceptedPackageCompleteness(
            PlanningRuntimeFacts runtimeFacts,
            List<ImplementationOutlineSubtask> subtasks
    ) {
        if (subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        for (ImplementationOutlineSubtask subtask : subtasks) {
            ScopePaths scopePaths = ScopePaths.fromOutlineSubtask(subtask);
            if (!scopePaths.requiresHostEntryPatch(runtimeFacts)) {
                continue;
            }
            issues.add(new GateIssue(
                    "PLAN_RUNTIME_" + issueIndex++,
                    "Outline 子任务把新的 runtime split 拆成了不完整 package：新增 runtime 脚本时，必须在同一子任务里同步携带宿主 HTML patch。",
                    GateFailureDisposition.REPLAN_CURRENT_STAGE,
                    GateIssueContext.forPlanningUnitPaths(
                            ImplementationPlanningUnitKind.OUTLINE,
                            OUTLINE_UNIT_ID,
                            scopePaths.normalizedPathStrings()
                    )
            ));
        }
        return List.copyOf(issues);
    }

    private int appendContinuationIssues(
            ImplementationContinuationConstraints constraints,
            List<GateIssue> issues,
            int issueIndex,
            DeliveryMode deliveryMode,
            String path
    ) {
        if (deliveryMode == DeliveryMode.SKELETON && constraints.marksExistingPath(path)) {
            issues.add(new GateIssue(
                    "PLAN_CONTINUATION_" + issueIndex++,
                    "Continuation 计划不能把已存在文件重新规划为 SKELETON: " + path,
                    GateFailureDisposition.REPLAN_CURRENT_STAGE,
                    GateIssueContext.forPlanningUnitPath(
                            ImplementationPlanningUnitKind.OUTLINE,
                            OUTLINE_UNIT_ID,
                            path
                    )
            ));
        }
        if (deliveryMode == DeliveryMode.REWORK && constraints.protectsHtmlEntry(path)) {
            issues.add(new GateIssue(
                    "PLAN_CONTINUATION_" + issueIndex++,
                    "Continuation 计划不能对现有 HTML 入口使用 REWORK/整页重写: " + path,
                    GateFailureDisposition.REPLAN_CURRENT_STAGE,
                    GateIssueContext.forPlanningUnitPath(
                            ImplementationPlanningUnitKind.OUTLINE,
                            OUTLINE_UNIT_ID,
                            path
                    )
            ));
        }
        return issueIndex;
    }

    private record ScopePaths(List<ScopedPath> normalizedPaths) {

        static ScopePaths fromOutlineSubtask(ImplementationOutlineSubtask subtask) {
            if (subtask == null) {
                return new ScopePaths(List.of());
            }
            return fromPaths(subtask.targetPaths());
        }

        static ScopePaths fromDetailChanges(List<ImplementationSubtaskDetailChange> changes) {
            if (changes == null || changes.isEmpty()) {
                return new ScopePaths(List.of());
            }
            return new ScopePaths(changes.stream()
                    .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                    .map(change -> new ScopedPath(
                            Path.of(change.path()).normalize(),
                            change.action(),
                            change.runtimeScriptRole()
                    ))
                    .distinct()
                    .toList());
        }

        static ScopePaths fromDetail(ImplementationSubtaskDetail detail) {
            return detail == null ? new ScopePaths(List.of()) : fromDetailChanges(detail.changes());
        }

        private static ScopePaths fromPaths(List<String> rawPaths) {
            if (rawPaths == null || rawPaths.isEmpty()) {
                return new ScopePaths(List.of());
            }
            return new ScopePaths(rawPaths.stream()
                    .filter(path -> path != null && !path.isBlank())
                    .map(path -> new ScopedPath(Path.of(path).normalize(), null, null))
                    .distinct()
                    .toList());
        }

        boolean requiresHostEntryPatch(PlanningRuntimeFacts runtimeFacts) {
            if (runtimeFacts == null || !runtimeFacts.hasResolvedHtmlEntry() || normalizedPaths.isEmpty()) {
                return false;
            }
            Path htmlEntryPath = runtimeFacts.htmlEntryPath();
            if (normalizedPaths.stream().map(ScopedPath::path).anyMatch(runtimeFacts::matchesHtmlEntry)) {
                return false;
            }
            Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
            List<Path> scopedRuntimePaths = normalizedPaths.stream()
                    .map(ScopedPath::path)
                    .filter(ProjectPathSupport::isRuntimeScript)
                    .filter(path -> isUnderHtmlEntryTree(path, htmlParent))
                    .toList();
            if (scopedRuntimePaths.isEmpty()) {
                return false;
            }
            List<Path> reachableRuntimePaths = runtimeFacts.reachableRuntimePaths();
            List<Path> runtimeRootPaths = runtimeFacts.runtimeRootPaths();
            List<Path> unresolvedRuntimePaths = scopedRuntimePaths.stream()
                    .filter(path -> !reachableRuntimePaths.contains(path))
                    .toList();
            if (unresolvedRuntimePaths.isEmpty()) {
                return false;
            }
            boolean hasReachableAnchor = scopedRuntimePaths.stream().anyMatch(reachableRuntimePaths::contains);
            if (!hasReachableAnchor) {
                return true;
            }
            return unresolvedRuntimePaths.stream().anyMatch(runtimeRootPaths::contains);
        }

        List<String> assessStructuredRuntimePackage(PlanningRuntimeFacts runtimeFacts) {
            if (runtimeFacts == null || !runtimeFacts.hasResolvedHtmlEntry() || normalizedPaths.isEmpty()) {
                return List.of();
            }
            Path htmlEntryPath = runtimeFacts.htmlEntryPath();
            boolean hasHostHtmlPatch = normalizedPaths.stream().map(ScopedPath::path).anyMatch(runtimeFacts::matchesHtmlEntry);
            Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
            List<String> issues = new ArrayList<>();
            for (ScopedPath scopedPath : normalizedPaths) {
                if (scopedPath.runtimeScriptRole() == null) {
                    continue;
                }
                Path path = scopedPath.path();
                if (runtimeFacts.matchesHtmlEntry(path)
                        || !ProjectPathSupport.isRuntimeScript(path)
                        || !isUnderHtmlEntryTree(path, htmlParent)) {
                    issues.add("runtimeScriptRole 只能用于当前 HTML 入口树下的新增 runtime 脚本: " + path);
                }
            }
            List<ScopedPath> scopedRuntimePaths = normalizedPaths.stream()
                    .filter(scopedPath -> ProjectPathSupport.isRuntimeScript(scopedPath.path()))
                    .filter(scopedPath -> isUnderHtmlEntryTree(scopedPath.path(), htmlParent))
                    .toList();
            if (scopedRuntimePaths.isEmpty()) {
                return issues.stream().distinct().toList();
            }
            List<Path> reachableRuntimePaths = runtimeFacts.reachableRuntimePaths();
            List<Path> runtimeRootPaths = runtimeFacts.runtimeRootPaths();
            boolean hasReachableAnchor = scopedRuntimePaths.stream()
                    .map(ScopedPath::path)
                    .anyMatch(reachableRuntimePaths::contains);
            boolean requiresHostEntryPatch = false;
            for (ScopedPath scopedPath : scopedRuntimePaths) {
                Path path = scopedPath.path();
                if (scopedPath.action() == ChangeAction.DELETE) {
                    if (scopedPath.runtimeScriptRole() != null) {
                        issues.add("删除 runtime 脚本时不允许声明 runtimeScriptRole: " + path);
                    }
                    continue;
                }
                PlanningRuntimeScriptRole runtimeScriptRole = scopedPath.runtimeScriptRole();
                boolean reachable = reachableRuntimePaths.contains(path);
                boolean knownRoot = runtimeRootPaths.contains(path);
                if (reachable) {
                    if (runtimeScriptRole != null) {
                        issues.add("当前子任务把已存在于 reachable runtime graph 的脚本声明了 runtimeScriptRole，只有新增 runtime 脚本才允许声明角色: " + path);
                    }
                    continue;
                }
                if (runtimeScriptRole == null) {
                    issues.add("当前子任务新增了 runtime 脚本，但没有声明 runtimeScriptRole=ROOT|LEAF: " + path);
                    continue;
                }
                if (runtimeScriptRole == PlanningRuntimeScriptRole.LEAF) {
                    if (!hasReachableAnchor) {
                        issues.add("当前子任务把 runtime 脚本标记为 LEAF，但同包没有当前 reachable runtime anchor: " + path);
                        continue;
                    }
                    if (knownRoot) {
                        issues.add("当前子任务把已知 runtime root 标记为 LEAF，这是不合法的: " + path);
                    }
                    continue;
                }
                if (runtimeScriptRole == PlanningRuntimeScriptRole.ROOT) {
                    requiresHostEntryPatch = true;
                }
            }
            if (requiresHostEntryPatch && !hasHostHtmlPatch) {
                issues.add("当前子任务把新的 runtime root 拆成了不完整 package：新增 runtime root 时，必须在同一子任务里同步携带宿主 HTML patch。");
            }
            return issues.stream().distinct().toList();
        }

        List<String> normalizedPathStrings() {
            return normalizedPaths.stream()
                    .map(scopedPath -> scopedPath.path().toString().replace('\\', '/'))
                    .toList();
        }

        private static boolean isUnderHtmlEntryTree(Path candidate, Path htmlParent) {
            Path candidateParent = candidate.getParent() == null ? Path.of("") : candidate.getParent().normalize();
            return htmlParent.toString().isBlank()
                    || candidateParent.equals(htmlParent)
                    || candidateParent.startsWith(htmlParent);
        }

        private record ScopedPath(
                Path path,
                ChangeAction action,
                PlanningRuntimeScriptRole runtimeScriptRole
        ) {
        }
    }
}
