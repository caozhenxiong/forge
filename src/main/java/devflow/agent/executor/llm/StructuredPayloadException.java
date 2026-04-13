package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 结构化模型载荷解析失败时抛出的异常。
 */
public class StructuredPayloadException extends IllegalStateException {

    private final StructuredPayloadFailureReason reason;

    public StructuredPayloadException(StructuredPayloadFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public StructuredPayloadException(StructuredPayloadFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public StructuredPayloadFailureReason reason() {
        return reason;
    }
}
