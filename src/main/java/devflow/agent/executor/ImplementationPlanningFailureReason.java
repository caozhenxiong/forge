package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * implementation planning 的稳定失败类型。
 *
 * <p>Flow / retry 层只根据这些 typed reason 做控制流判断，
 * 不再依赖异常消息文本。
 */
enum ImplementationPlanningFailureReason {
    COVERAGE_MISMATCH(true),
    PLAN_GENERATION_EXHAUSTED(true),
    PLAN_PARSE_FAILED(true),
    INVALID_PLAN_SCHEMA(false);

    private final boolean recoverable;

    ImplementationPlanningFailureReason(boolean recoverable) {
        this.recoverable = recoverable;
    }

    boolean recoverable() {
        return recoverable;
    }
}
