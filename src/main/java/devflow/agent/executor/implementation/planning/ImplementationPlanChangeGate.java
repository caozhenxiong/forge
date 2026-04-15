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
            List<Subtask> subtasks
    ) {
        if (subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>(evaluateContinuationConstraints(continuationConstraints, subtasks));
        issues.addAll(evaluateAcceptedPackageCompleteness(runtimeFacts, subtasks));
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
        if (!scopePaths.requiresHostEntryPatch(runtimeFacts)) {
            return List.of();
        }
        return List.of(new GateIssue(
                "PLAN_RUNTIME_1",
                "当前子任务把新的 runtime split 拆成了不完整 package：新增 runtime 脚本时，必须在同一子任务里同步携带宿主 HTML patch。",
                GateFailureDisposition.REPLAN_CURRENT_STAGE,
                GateIssueContext.forPlanningUnitPaths(
                        ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                        subtaskId == null || subtaskId.isBlank() ? OUTLINE_UNIT_ID : subtaskId,
                        scopePaths.normalizedPathStrings()
                )
        ));
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
            List<Subtask> subtasks
    ) {
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        for (Subtask subtask : subtasks) {
            ScopePaths scopePaths = ScopePaths.fromSubtask(subtask);
            if (!scopePaths.requiresHostEntryPatch(runtimeFacts)) {
                continue;
            }
            issues.add(new GateIssue(
                    "PLAN_RUNTIME_" + issueIndex++,
                    "当前 implementation plan 把新的 runtime split 拆成了不完整 package：新增 runtime 脚本时，必须在同一子任务里同步携带宿主 HTML patch。",
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

    private record ScopePaths(List<Path> normalizedPaths) {

        static ScopePaths fromSubtask(Subtask subtask) {
            if (subtask == null || subtask.changes() == null) {
                return new ScopePaths(List.of());
            }
            return fromPaths(subtask.changes().stream()
                    .map(FileChange::path)
                    .toList());
        }

        static ScopePaths fromOutlineSubtask(ImplementationOutlineSubtask subtask) {
            if (subtask == null) {
                return new ScopePaths(List.of());
            }
            return fromPaths(subtask.targetPaths());
        }

        static ScopePaths fromDetailChanges(List<ImplementationSubtaskDetailChange> changes) {
            return fromPaths(changes.stream()
                    .map(ImplementationSubtaskDetailChange::path)
                    .toList());
        }

        private static ScopePaths fromPaths(List<String> rawPaths) {
            if (rawPaths == null || rawPaths.isEmpty()) {
                return new ScopePaths(List.of());
            }
            return new ScopePaths(rawPaths.stream()
                    .filter(path -> path != null && !path.isBlank())
                    .map(path -> Path.of(path).normalize())
                    .distinct()
                    .toList());
        }

        boolean requiresHostEntryPatch(PlanningRuntimeFacts runtimeFacts) {
            if (runtimeFacts == null || !runtimeFacts.hasResolvedHtmlEntry() || normalizedPaths.isEmpty()) {
                return false;
            }
            Path htmlEntryPath = runtimeFacts.htmlEntryPath();
            if (normalizedPaths.stream().anyMatch(runtimeFacts::matchesHtmlEntry)) {
                return false;
            }
            Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
            List<Path> scopedRuntimePaths = normalizedPaths.stream()
                    .filter(ProjectPathSupport::isRuntimeScript)
                    .filter(path -> isUnderHtmlEntryTree(path, htmlParent))
                    .toList();
            if (scopedRuntimePaths.isEmpty()) {
                return false;
            }
            List<Path> wiredRuntimePaths = runtimeFacts.wiredRuntimePaths();
            return scopedRuntimePaths.stream().anyMatch(path -> !wiredRuntimePaths.contains(path));
        }

        List<String> normalizedPathStrings() {
            return normalizedPaths.stream()
                    .map(path -> path.toString().replace('\\', '/'))
                    .toList();
        }

        private static boolean isUnderHtmlEntryTree(Path candidate, Path htmlParent) {
            Path candidateParent = candidate.getParent() == null ? Path.of("") : candidate.getParent().normalize();
            return htmlParent.toString().isBlank()
                    || candidateParent.equals(htmlParent)
                    || candidateParent.startsWith(htmlParent);
        }
    }
}
