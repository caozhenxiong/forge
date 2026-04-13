package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.ModelBudgetProfile;
import devflow.agent.executor.llm.ModelBudgetRegistry;

import org.springframework.stereotype.Component;

/**
 * 在真正调用模型前，先判断上下文是否需要 compact。
 *
 * <p>预算公式显式收成三层：
 * 1. `C_fixed`：当前先映射为 `systemPrompt`；
 * 2. `C_retrieved`：当前先映射为 `userPrompt`；
 * 3. `C_out`：`reserveTokens + minimumOutputTokens`。
 *
 * <p>因此当前请求必须满足：
 * `C_fixed + C_retrieved + C_out <= W`
 *
 * <p>当预算超限时，优先保留 `C_out`，再裁 `C_retrieved`；只有 `C_fixed` 本身已经过大时，才会一起压缩。
 */
@Component
public class ContextBudgetPlanner {

    private final ModelBudgetRegistry modelBudgetRegistry;
    private final PromptTokenEstimator promptTokenEstimator;

    public ContextBudgetPlanner(
            ModelBudgetRegistry modelBudgetRegistry,
            PromptTokenEstimator promptTokenEstimator
    ) {
        this.modelBudgetRegistry = modelBudgetRegistry;
        this.promptTokenEstimator = promptTokenEstimator;
    }

    public ContextBudgetPlan plan(String modelName, String systemPrompt, String userPrompt) {
        ModelBudgetProfile budgetProfile = modelBudgetRegistry.resolve(modelName);
        ContextBudgetBreakdown breakdown = breakdown(modelName, budgetProfile, systemPrompt, userPrompt);
        int totalInputBudget = Math.max(1, breakdown.contextWindowTokens() - breakdown.outputReserveTokens());
        if (breakdown.fixedTokens() + breakdown.retrievedTokens() <= totalInputBudget) {
            return new ContextBudgetPlan(
                    false,
                    breakdown.estimatedPromptTokens(),
                    breakdown.fixedTokens(),
                    breakdown.retrievedTokens(),
                    breakdown.outputReserveTokens(),
                    breakdown.materialBudgetTokens(),
                    safeLength(systemPrompt),
                    safeLength(userPrompt)
            );
        }
        int[] charBudgets = allocateCharBudgets(systemPrompt, userPrompt, breakdown, totalInputBudget);
        return new ContextBudgetPlan(
                true,
                breakdown.estimatedPromptTokens(),
                breakdown.fixedTokens(),
                breakdown.retrievedTokens(),
                breakdown.outputReserveTokens(),
                breakdown.materialBudgetTokens(),
                charBudgets[0],
                charBudgets[1]
        );
    }

    /**
     * compact 预算按原始 system/user 体量比例分配，但不会把非空部分压成 0。
     *
     * <p>这里不引入新的魔法阈值，而是用“每个非空部分至少保留 1 个字符”的下限，
     * 剩余预算再按体量比例分配。
     */
    private ContextBudgetBreakdown breakdown(
            String modelName,
            ModelBudgetProfile budgetProfile,
            String systemPrompt,
            String userPrompt
    ) {
        int fixedTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, systemPrompt);
        int retrievedTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, userPrompt);
        int outputReserveTokens = Math.max(
                budgetProfile.minimumOutputTokens(),
                Math.min(
                        budgetProfile.contextWindowTokens() - 1,
                        budgetProfile.reserveTokens() + budgetProfile.minimumOutputTokens()
                )
        );
        int materialBudgetTokens = Math.max(
                1,
                budgetProfile.contextWindowTokens() - outputReserveTokens - fixedTokens
        );
        return new ContextBudgetBreakdown(
                budgetProfile.contextWindowTokens(),
                fixedTokens,
                retrievedTokens,
                outputReserveTokens,
                materialBudgetTokens,
                fixedTokens + retrievedTokens
        );
    }

    private int[] allocateCharBudgets(
            String systemPrompt,
            String userPrompt,
            ContextBudgetBreakdown breakdown,
            int totalInputBudget
    ) {
        int systemLength = safeLength(systemPrompt);
        int userLength = safeLength(userPrompt);
        if (systemLength + userLength <= 0 || breakdown.estimatedPromptTokens() <= 0) {
            return new int[]{systemLength, userLength};
        }
        if (breakdown.fixedTokens() < totalInputBudget) {
            int userCharBudget = scaleChars(userLength, breakdown.retrievedTokens(), breakdown.materialBudgetTokens());
            return new int[]{systemLength, userCharBudget};
        }
        int totalLength = systemLength + userLength;
        int minimumSystem = systemLength > 0 ? 1 : 0;
        int minimumUser = userLength > 0 ? 1 : 0;
        int allowedChars = Math.min(
                totalLength,
                Math.max(minimumSystem + minimumUser, scaleChars(totalLength, breakdown.estimatedPromptTokens(), totalInputBudget))
        );
        int systemBudget = Math.min(
                systemLength,
                Math.max(minimumSystem, scaleChars(systemLength, breakdown.fixedTokens(), totalInputBudget))
        );
        int userBudget = Math.min(
                userLength,
                Math.max(minimumUser, allowedChars - systemBudget)
        );
        if (systemBudget + userBudget > allowedChars) {
            int overflow = systemBudget + userBudget - allowedChars;
            if (systemBudget - overflow >= minimumSystem) {
                systemBudget -= overflow;
            } else {
                userBudget = Math.max(minimumUser, userBudget - overflow);
            }
        }
        return new int[]{systemBudget, userBudget};
    }

    private int scaleChars(int chars, int sourceTokens, int targetTokens) {
        if (chars <= 0 || sourceTokens <= 0 || targetTokens <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(chars, (int) Math.floor(chars * ((double) targetTokens / sourceTokens))));
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
