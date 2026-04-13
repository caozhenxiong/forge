package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.subtask.Subtask;
/**
 * implementation planning 中与具体文件变更声明相关的最小 gate。
 *
 * <p>Claude 风格下，planning 不再决定 editScope / ownership / wiring 语义。
 * 这里仅保留 continuation 不能回退既有实现形态这类稳定约束；
 * 运行时接线和入口归属改由实现后的 verifier 基于产物事实裁决。
 */
final class ImplementationPlanChangeGate {

    private static final String OUTLINE_UNIT_ID = "outline";

    List<GateIssue> evaluate(
            ImplementationContinuationConstraints continuationConstraints,
            List<Subtask> subtasks
    ) {
        if (subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        return evaluateContinuationConstraints(continuationConstraints, subtasks);
    }

    List<GateIssue> evaluateOutlineContinuationConstraints(
            ImplementationContinuationConstraints constraints,
            List<ImplementationOutlineSubtask> subtasks
    ) {
        if (constraints == null || !constraints.active() || subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        List<GateIssue> issues = new ArrayList<>();
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
}
