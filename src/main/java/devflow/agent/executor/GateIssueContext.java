package devflow.agent.executor;

import java.util.List;

/**
 * gate issue 的确定性上下文。
 *
 * <p>这里承载的是流程层后续路由所需的稳定字段，例如受影响路径。
 * 这样 planning / repair 不必再去解析 message 文本才能判断该回退哪个单元。
 */
record GateIssueContext(
        List<String> paths,
        ImplementationPlanningUnitKind planningUnitKind,
        String planningUnitId
) {

    GateIssueContext {
        paths = paths == null ? List.of() : paths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        planningUnitId = planningUnitId == null ? "" : planningUnitId.trim();
    }

    static GateIssueContext empty() {
        return new GateIssueContext(List.of(), null, "");
    }

    static GateIssueContext forPath(String path) {
        return new GateIssueContext(List.of(path), null, "");
    }

    static GateIssueContext forPaths(List<String> paths) {
        return new GateIssueContext(paths, null, "");
    }

    static GateIssueContext forPlanningUnit(
            ImplementationPlanningUnitKind unitKind,
            String unitId
    ) {
        return new GateIssueContext(List.of(), unitKind, unitId);
    }

    static GateIssueContext forPlanningUnitPath(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            String path
    ) {
        return new GateIssueContext(List.of(path), unitKind, unitId);
    }

    static GateIssueContext forPlanningUnitPaths(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            List<String> paths
    ) {
        return new GateIssueContext(paths, unitKind, unitId);
    }
}
