package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.PlaceholderValues;
import org.springframework.stereotype.Component;

/**
 * 根据 {@link ContextBudgetPlan} 对 prompt 做确定性 compact。
 *
 * <p>当前实现不尝试“理解语义后智能摘要”，而是先做稳定的结构截断：
 * 1. 未超预算时直接透传；
 * 2. 超预算时先保留 `C_out`，再裁输入材料；
 * 3. 正常情况下优先保留 `systemPrompt`，先压 `userPrompt`；
 * 4. 只有固定开销本身过大时，才同时裁 system/user。
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

    public CompactedPrompt compact(String modelName, String systemPrompt, String userPrompt) {
        ContextBudgetPlan budgetPlan = contextBudgetPlanner.plan(modelName, systemPrompt, userPrompt);
        if (!budgetPlan.compactRequired()) {
            return new CompactedPrompt(
                    safeValue(systemPrompt),
                    safeValue(userPrompt),
                    budgetPlan
            );
        }
        return new CompactedPrompt(
                compactValue(systemPrompt, budgetPlan.systemCharBudget()),
                compactValue(userPrompt, budgetPlan.userCharBudget()),
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
