package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 记录一次模型调用在发送前的上下文预算决策。
 *
 * <p>这层只回答两个问题：
 * 1. 当前 prompt 是否已经超出安全上下文预算；
 * 2. 如果要 compact，system/user 各自最多还能保留多少字符。
 *
 * <p>它不负责真正截断字符串，也不负责输出预算裁剪。
 */
public record ContextBudgetPlan(
        boolean compactRequired,
        int estimatedPromptTokens,
        int fixedTokens,
        int retrievedTokens,
        int outputReserveTokens,
        int materialBudgetTokens,
        int systemCharBudget,
        int userCharBudget
) {
}
