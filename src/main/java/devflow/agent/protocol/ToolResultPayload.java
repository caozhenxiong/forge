package devflow.agent.protocol;

/**
 * 供 machine-readable artifact block 使用的工具结果载荷。
 *
 * <p>这里故意只保留流程层真正需要消费的稳定字段，
 * 避免 orchestrator 直接依赖执行层内部对象。
 */
public record ToolResultPayload(
        String toolName,
        String status,
        String failureCode,
        String evidence,
        String recommendedNextAction
) {
}
