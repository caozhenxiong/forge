package devflow.agent.protocol;

/**
 * review history 的机器协议。
 *
 * <p>流程层读取 review history 时，只依赖这份结构化 entry，
 * 不再从 markdown 行文本里猜 decision、summary、evidence 或 actionItems。
 */
public record ReviewHistoryEntryPayload(
        int attempt,
        String reviewer,
        String stage,
        String decision,
        String fixMode,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems
) {
}
