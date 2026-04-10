package devflow.agent.executor;

/**
 * 结构化模型载荷解析失败时抛出的异常。
 */
class StructuredPayloadException extends IllegalStateException {

    private final StructuredPayloadFailureReason reason;

    StructuredPayloadException(StructuredPayloadFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    StructuredPayloadException(StructuredPayloadFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    StructuredPayloadFailureReason reason() {
        return reason;
    }
}
