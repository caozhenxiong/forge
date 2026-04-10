package devflow.agent.executor;

/**
 * 单条 gate 失败项。
 *
 * <p>code 用于稳定分类，message 面向日志与反馈，disposition 则交给流程层决定下一步走向。
 */
public record GateIssue(
        String code,
        String message,
        GateFailureDisposition disposition
) {

    public GateIssue {
        code = code == null ? "" : code.trim();
        message = message == null ? "" : message.trim();
        disposition = disposition == null ? GateFailureDisposition.ESCALATE : disposition;
    }
}
