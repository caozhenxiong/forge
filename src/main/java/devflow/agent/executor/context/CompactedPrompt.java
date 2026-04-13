package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.llm.LlmPromptContext;

/**
 * 统一承载 compact 后真正发给模型的 prompt。
 *
 * <p>compact-first 不应该只是“偷偷改字符串”，而应该把：
 * 1. 原始预算决策；
 * 2. compact 后的 system/user prompt；
 * 一起保留下来，便于后续预算、日志和测试校验。
 */
public record CompactedPrompt(
        String systemPrompt,
        LlmPromptContext promptContext,
        ContextBudgetPlan budgetPlan
) {
    public String userPrompt() {
        return promptContext == null ? "" : promptContext.render();
    }
}
