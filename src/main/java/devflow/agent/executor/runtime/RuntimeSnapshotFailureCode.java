package devflow.agent.executor.runtime;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 运行时快照采集失败的稳定原因码。
 */
public enum RuntimeSnapshotFailureCode {
    NONE,
    COLLECTOR_EXECUTION_FAILED,
    PAYLOAD_INVALID
}
