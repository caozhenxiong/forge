package devflow.agent.editing;

/**
 * 精确编辑失败时抛出的结构化异常。
 *
 * <p>调用方应根据 {@link #reason()} 做流程分流，而不是解析消息文本。
 */
public class PreciseEditException extends IllegalStateException {

    private final PreciseEditFailureReason reason;

    public PreciseEditException(PreciseEditFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PreciseEditFailureReason reason() {
        return reason;
    }
}
