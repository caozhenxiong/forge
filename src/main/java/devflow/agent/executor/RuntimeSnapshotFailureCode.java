package devflow.agent.executor;

/**
 * 运行时快照采集失败的稳定原因码。
 */
public enum RuntimeSnapshotFailureCode {
    NONE,
    COLLECTOR_EXECUTION_FAILED,
    PAYLOAD_INVALID
}
