package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

/**
 * 把最终 plan gate 的失败项路由回 outline 或具体 subtask detail。
 *
 * <p>路由规则只消费 gate issue 里显式携带的 planning unit 上下文，
 * 不再通过 code 前缀或自然语言 prose 反推该重试哪一层。
 */
final class ImplementationPlanningFeedbackRouter {

    RouteDecision route(
            GateReport report,
            ImplementationOutline outline
    ) {
        if (report == null || report.passed() || outline == null || outline.subtasks() == null || outline.subtasks().isEmpty()) {
            return null;
        }
        for (GateIssue issue : report.issues()) {
            if (issue == null) {
                continue;
            }
            RouteDecision decision = routeStructuredIssue(issue, outline);
            if (decision != null) {
                return decision;
            }
        }
        return new RouteDecision(
                ImplementationPlanningUnitKind.OUTLINE,
                "outline",
                report.summary()
        );
    }

    private RouteDecision routeStructuredIssue(
            GateIssue issue,
            ImplementationOutline outline
    ) {
        GateIssueContext context = issue.context();
        if (context == null || context.planningUnitKind() == null) {
            return null;
        }
        if (context.planningUnitKind() == ImplementationPlanningUnitKind.OUTLINE) {
            return new RouteDecision(
                    ImplementationPlanningUnitKind.OUTLINE,
                    "outline",
                    issue.message()
            );
        }
        String subtaskId = blankIfNull(context.planningUnitId());
        if (subtaskId.isBlank()) {
            subtaskId = resolveSubtaskId(outline, context.paths());
        }
        if (subtaskId.isBlank()) {
            return null;
        }
        return new RouteDecision(
                ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                subtaskId,
                issue.message()
        );
    }

    private String resolveSubtaskId(ImplementationOutline outline, List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        String resolvedSubtaskId = null;
        for (ImplementationOutlineSubtask subtask : outline.subtasks()) {
            if (subtask == null || subtask.targetPaths() == null) {
                continue;
            }
            for (String path : subtask.targetPaths()) {
                if (path == null || path.isBlank() || !paths.contains(path.trim())) {
                    continue;
                }
                resolvedSubtaskId = subtask.id();
            }
        }
        return blankIfNull(resolvedSubtaskId);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    record RouteDecision(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            String feedback
    ) {
    }
}
