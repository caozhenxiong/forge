package devflow.agent.executor;

/**
 * 统一承载 compact 后真正发给模型的 prompt。
 *
 * <p>compact-first 不应该只是“偷偷改字符串”，而应该把：
 * 1. 原始预算决策；
 * 2. compact 后的 system/user prompt；
 * 一起保留下来，便于后续预算、日志和测试校验。
 */
record CompactedPrompt(
        String systemPrompt,
        String userPrompt,
        ContextBudgetPlan budgetPlan
) {
}
