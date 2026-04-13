package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 单次请求的分层预算拆解。
 *
 * <p>当前 generate 主链把用户侧上下文拆成 durable / working / evidence / trace 四层；
 * 这里负责保留每层的 token 拆解，供 compactor 按固定优先级裁剪。
 */
record ContextBudgetBreakdown(
        int contextWindowTokens,
        int fixedTokens,
        int durableTokens,
        int workingTokens,
        int evidenceTokens,
        int traceTokens,
        int retrievedTokens,
        int outputReserveTokens,
        int materialBudgetTokens,
        int estimatedPromptTokens
) {
}
