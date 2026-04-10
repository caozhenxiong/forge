package devflow.agent.executor;

/**
 * 描述当前项目在测试阶段应该使用什么执行工具。
 *
 * <p>这个对象只表达确定性选择结果，不直接推进流程。
 * 当工具不可用时，它同时携带稳定的失败原因和证据文案，
 * 供 TestExecutor 构造一致的 testcase 结果。
 */
public record TestToolSelection(
        TestExecutionTool tool,
        TestToolFailureReason failureReason,
        String details,
        String evidence,
        String runtimeSnapshotEntry
) {

    public boolean executable() {
        return tool != null && tool != TestExecutionTool.UNAVAILABLE;
    }

    public boolean supportsRuntimeSnapshot() {
        return tool == TestExecutionTool.PLAYWRIGHT
                && runtimeSnapshotEntry != null
                && !runtimeSnapshotEntry.isBlank();
    }
}
