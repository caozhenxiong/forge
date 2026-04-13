package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationTelemetry;

/**
 * implementation planning 的 typed 异常。
 *
 * <p>这层专门替代“靠异常消息 startsWith/contains 做流程判断”的旧做法。
 */
class ImplementationPlanningException extends RuntimeException {

    private final ImplementationPlanningFailureReason reason;
    private final GenerationTelemetry telemetry;

    ImplementationPlanningException(ImplementationPlanningFailureReason reason, String message) {
        this(reason, message, null, null);
    }

    ImplementationPlanningException(ImplementationPlanningFailureReason reason, String message, Throwable cause) {
        this(reason, message, cause, null);
    }

    ImplementationPlanningException(
            ImplementationPlanningFailureReason reason,
            String message,
            Throwable cause,
            GenerationTelemetry telemetry
    ) {
        super(message, cause);
        this.reason = reason;
        this.telemetry = telemetry;
    }

    ImplementationPlanningFailureReason reason() {
        return reason;
    }

    GenerationTelemetry telemetry() {
        return telemetry;
    }
}
