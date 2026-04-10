package devflow.agent.executor;

/**
 * 统一承载 LLM 调用层的失败类型，避免上层再通过 message.contains(...) 猜原因。
 */
public class LlmInvocationException extends IllegalStateException {

    private final LlmFailureReason reason;

    public LlmInvocationException(LlmFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LlmInvocationException(LlmFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public LlmFailureReason reason() {
        return reason;
    }
}
