package devflow.agent.executor;

import java.util.List;

/**
 * 把最终 plan gate 的失败项路由回 outline 或具体 subtask detail。
 *
 * <p>路由规则只依赖确定性 gate code 和 targetPaths 命中关系，
 * 不解析自然语言 prose。
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
            if (issue.code().startsWith("PLAN_EXECUTION_CONTRACT_")
                    || issue.code().startsWith("PLAN_RUNNABLE_MILESTONE_")
                    || issue.code().startsWith("PLAN_OUTLINE_")) {
                return new RouteDecision(
                        ImplementationPlanningUnitKind.OUTLINE,
                        "outline",
                        issue.message()
                );
            }
            if (issue.code().startsWith("PLAN_RUNTIME_") || issue.code().startsWith("PLAN_CONTINUATION_")) {
                String subtaskId = resolveSubtaskId(outline, issue.context());
                if (subtaskId != null) {
                    return new RouteDecision(
                            ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                            subtaskId,
                            issue.message()
                    );
                }
            }
        }
        return new RouteDecision(
                ImplementationPlanningUnitKind.OUTLINE,
                "outline",
                report.summary()
        );
    }

    private String resolveSubtaskId(ImplementationOutline outline, GateIssueContext context) {
        if (context == null || context.paths().isEmpty()) {
            return null;
        }
        String resolvedSubtaskId = null;
        for (ImplementationOutlineSubtask subtask : outline.subtasks()) {
            if (subtask == null || subtask.targetPaths() == null) {
                continue;
            }
            for (String path : subtask.targetPaths()) {
                if (path == null || path.isBlank() || !context.paths().contains(path.trim())) {
                    continue;
                }
                resolvedSubtaskId = subtask.id();
            }
        }
        return resolvedSubtaskId == null || resolvedSubtaskId.isBlank() ? null : resolvedSubtaskId;
    }

    record RouteDecision(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            String feedback
    ) {
    }
}
