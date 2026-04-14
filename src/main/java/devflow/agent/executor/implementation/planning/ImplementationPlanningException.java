package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationTelemetry;

/**
 * implementation planning 的 typed 异常。
 *
 * <p>这层专门替代“靠异常消息 startsWith/contains 做流程判断”的旧做法。
 */
public class ImplementationPlanningException extends RuntimeException {

    private final ImplementationPlanningFailureReason reason;
    private final GenerationTelemetry telemetry;

    public ImplementationPlanningException(ImplementationPlanningFailureReason reason, String message) {
        this(reason, message, null, null);
    }

    public ImplementationPlanningException(ImplementationPlanningFailureReason reason, String message, Throwable cause) {
        this(reason, message, cause, null);
    }

    public ImplementationPlanningException(
            ImplementationPlanningFailureReason reason,
            String message,
            Throwable cause,
            GenerationTelemetry telemetry
    ) {
        super(message, cause);
        this.reason = reason;
        this.telemetry = telemetry;
    }

    public ImplementationPlanningFailureReason reason() {
        return reason;
    }

    public GenerationTelemetry telemetry() {
        return telemetry;
    }
}
