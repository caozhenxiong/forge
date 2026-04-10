package devflow.agent.protocol;

/**
 * review artifact 的机器协议。
 *
 * <p>流程只读取这些结构化字段，不再从 review markdown prose 中猜 decision、fixMode
 * 或 blocking findings 的语义。
 */
public record ReviewArtifactPayload(
        String decision,
        String fixMode,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems,
        Boolean blockingFindings,
        Integer findingCount,
        Integer exitCode
) {
}
