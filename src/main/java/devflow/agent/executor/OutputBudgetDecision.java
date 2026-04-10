package devflow.agent.executor;

import java.util.Map;

/**
 * 一次输出预算计算的稳定快照。
 *
 * <p>把“请求输出上限、估算输入 token、实际生效输出预算、上下文窗口”收成一个对象，
 * 方便 provider telemetry、事件日志和测试统一消费，避免这些数字继续散在执行链里。
 */
public record OutputBudgetDecision(
        Map<String, Object> effectiveOptions,
        int requestedOutputTokens,
        int effectiveOutputTokens,
        int contextWindowTokens,
        int estimatedPromptTokens,
        int fixedTokens,
        int retrievedTokens,
        int outputReserveTokens,
        int materialBudgetTokens,
        int reserveTokens,
        int availableOutputTokens
) {
}
