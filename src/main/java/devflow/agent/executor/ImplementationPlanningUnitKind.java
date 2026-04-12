package devflow.agent.executor;

/**
 * implementation planning 内部的稳定规划单元类型。
 *
 * <p>当前只允许两类单元：
 * 1. OUTLINE：先决定任务拆分、覆盖责任和目标文件归属；
 * 2. SUBTASK_DETAIL：再为单个子任务补齐精确文件变更声明。
 */
enum ImplementationPlanningUnitKind {
    OUTLINE("outline"),
    SUBTASK_DETAIL("subtask-detail");

    private final String artifactKey;

    ImplementationPlanningUnitKind(String artifactKey) {
        this.artifactKey = artifactKey;
    }

    String artifactKey() {
        return artifactKey;
    }
}
