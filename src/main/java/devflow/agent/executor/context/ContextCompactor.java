package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmPromptContext;
import org.springframework.stereotype.Component;

/**
 * 根据 {@link ContextBudgetPlan} 对 prompt 做确定性 compact。
 *
 * <p>当前实现不尝试“理解语义后智能摘要”，而是先做稳定的结构截断：
 * 1. 未超预算时直接透传；
 * 2. 超预算时先保留 `C_out`，再裁输入材料；
 * 3. 用户侧上下文按 `trace -> evidence -> working -> durable` 的顺序收缩；
 * 4. 只有固定开销本身过大时，才压 `systemPrompt`。
 *
 * <p>这层是 compact-first 的最小可用版，后续如果接入更强的摘要器，也应该继续保留
 * 这个确定性兜底。
 */
@Component
public class ContextCompactor {

    private final ContextBudgetPlanner contextBudgetPlanner;

    public ContextCompactor(ContextBudgetPlanner contextBudgetPlanner) {
        this.contextBudgetPlanner = contextBudgetPlanner;
    }

    public CompactedPrompt compact(String modelName, LlmGenerateRequest request) {
        LlmGenerateRequest effectiveRequest = request == null
                ? new LlmGenerateRequest("", LlmPromptContext.empty(), java.util.Map.of(), null)
                : request;
        ContextBudgetPlan budgetPlan = contextBudgetPlanner.plan(
                modelName,
                effectiveRequest.systemPrompt(),
                effectiveRequest.promptContext()
        );
        if (!budgetPlan.compactRequired()) {
            return new CompactedPrompt(
                    safeValue(effectiveRequest.systemPrompt()),
                    effectiveRequest.promptContext(),
                    budgetPlan
            );
        }
        return new CompactedPrompt(
                compactValue(effectiveRequest.systemPrompt(), budgetPlan.systemCharBudget()),
                new LlmPromptContext(
                        compactValue(effectiveRequest.promptContext().durableContext(), budgetPlan.durableCharBudget()),
                        compactValue(effectiveRequest.promptContext().workingContext(), budgetPlan.workingCharBudget()),
                        compactValue(effectiveRequest.promptContext().evidenceContext(), budgetPlan.evidenceCharBudget()),
                        compactValue(effectiveRequest.promptContext().traceContext(), budgetPlan.traceCharBudget())
                ),
                budgetPlan
        );
    }

    private String compactValue(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (maxChars <= 0) {
            return "";
        }
        return PlaceholderValues.truncateMiddle(value, maxChars);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
