package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 描述当前实现子任务的交付模式。
 * 该模式用于约束生成范围、校验粒度和恢复策略，而不是表达阶段状态。
 */
public enum DeliveryMode {
    SKELETON,
    INCREMENTAL,
    PATCH,
    REWORK
}
