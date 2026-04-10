package devflow.agent.executor;

/**
 * 单次请求的分层预算拆解。
 *
 * <p>当前 Forge 的 prompt contract 还只有 `systemPrompt + userPrompt` 两层，因此这里先做显式映射：
 * 1. `C_fixed` 近似映射为 `systemPrompt`；
 * 2. `C_retrieved` 近似映射为 `userPrompt`；
 * 3. `C_out` 映射为 `reserveTokens + minimumOutputTokens`。
 *
 * <p>后续如果 prompt contract 再细分成 `base/state/retrieved/tool`，这层继续保持字段语义不变。
 */
record ContextBudgetBreakdown(
        int contextWindowTokens,
        int fixedTokens,
        int retrievedTokens,
        int outputReserveTokens,
        int materialBudgetTokens,
        int estimatedPromptTokens
) {
}
