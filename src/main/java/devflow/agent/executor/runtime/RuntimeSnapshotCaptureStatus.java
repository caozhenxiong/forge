package devflow.agent.executor.runtime;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 运行时快照的唯一采集状态。
 *
 * <p>这里要把“真实采集成功”和“采集链自身出错”显式区分开，
 * 避免主链继续把空快照当成成功事实使用。
 */
public enum RuntimeSnapshotCaptureStatus {
    CAPTURED,
    DERIVED_STATIC,
    COLLECTOR_FAILED,
    PAYLOAD_INVALID
}
