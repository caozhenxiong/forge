package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmPromptContext;
import devflow.agent.executor.llm.ModelBudgetProfile;
import devflow.agent.executor.llm.ModelBudgetRegistry;

import org.springframework.stereotype.Component;

/**
 * 在真正调用模型前，先判断上下文是否需要 compact。
 *
 * <p>预算公式显式收成三层：
 * 1. `C_fixed`：`systemPrompt`；
 * 2. `C_retrieved`：`durable + working + evidence + trace`；
 * 3. `C_out`：`reserveTokens + minimumOutputTokens`。
 *
 * <p>因此当前请求必须满足：
 * `C_fixed + C_retrieved + C_out <= W`
 *
 * <p>当预算超限时，优先保留 `C_out`，再按四层优先级裁用户侧上下文：
 * `trace -> evidence -> working -> durable`。只有 `systemPrompt` 本身已经过大时，才会压缩固定层。
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

    public ContextBudgetPlan plan(String modelName, String systemPrompt, LlmPromptContext promptContext) {
        ModelBudgetProfile budgetProfile = modelBudgetRegistry.resolve(modelName);
        LlmPromptContext layers = promptContext == null ? LlmPromptContext.empty() : promptContext;
        ContextBudgetBreakdown breakdown = breakdown(modelName, budgetProfile, systemPrompt, layers);
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
                    safeLength(layers.durableContext()),
                    safeLength(layers.workingContext()),
                    safeLength(layers.evidenceContext()),
                    safeLength(layers.traceContext())
            );
        }
        int systemCharBudget = systemCharBudget(systemPrompt, breakdown.fixedTokens(), totalInputBudget);
        int[] charBudgets = allocateLayerCharBudgets(layers, breakdown, totalInputBudget);
        return new ContextBudgetPlan(
                true,
                breakdown.estimatedPromptTokens(),
                breakdown.fixedTokens(),
                breakdown.retrievedTokens(),
                breakdown.outputReserveTokens(),
                breakdown.materialBudgetTokens(),
                systemCharBudget,
                charBudgets[0],
                charBudgets[1],
                charBudgets[2],
                charBudgets[3]
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
            LlmPromptContext promptContext
    ) {
        int fixedTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, systemPrompt);
        int durableTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, promptContext.durableContext());
        int workingTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, promptContext.workingContext());
        int evidenceTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, promptContext.evidenceContext());
        int traceTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, promptContext.traceContext());
        int retrievedTokens = durableTokens + workingTokens + evidenceTokens + traceTokens;
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
                durableTokens,
                workingTokens,
                evidenceTokens,
                traceTokens,
                retrievedTokens,
                outputReserveTokens,
                materialBudgetTokens,
                fixedTokens + retrievedTokens
        );
    }

    private int systemCharBudget(String systemPrompt, int fixedTokens, int totalInputBudget) {
        int systemLength = safeLength(systemPrompt);
        if (systemLength <= 0) {
            return 0;
        }
        if (fixedTokens <= totalInputBudget) {
            return systemLength;
        }
        return scaleChars(systemLength, fixedTokens, totalInputBudget);
    }

    private int[] allocateLayerCharBudgets(
            LlmPromptContext promptContext,
            ContextBudgetBreakdown breakdown,
            int totalInputBudget
    ) {
        if (breakdown.fixedTokens() >= totalInputBudget) {
            return new int[]{0, 0, 0, 0};
        }
        int remainingTokens = Math.max(0, totalInputBudget - breakdown.fixedTokens());
        int[] budgets = new int[4];
        LayerBudgetState durable = allocateLayer(promptContext.durableContext(), breakdown.durableTokens(), remainingTokens);
        budgets[0] = durable.charBudget();
        LayerBudgetState working = allocateLayer(promptContext.workingContext(), breakdown.workingTokens(), durable.remainingTokens());
        budgets[1] = working.charBudget();
        LayerBudgetState evidence = allocateLayer(promptContext.evidenceContext(), breakdown.evidenceTokens(), working.remainingTokens());
        budgets[2] = evidence.charBudget();
        LayerBudgetState trace = allocateLayer(promptContext.traceContext(), breakdown.traceTokens(), evidence.remainingTokens());
        budgets[3] = trace.charBudget();
        return budgets;
    }

    private LayerBudgetState allocateLayer(String content, int sourceTokens, int remainingTokens) {
        int length = safeLength(content);
        if (length <= 0 || sourceTokens <= 0 || remainingTokens <= 0) {
            return new LayerBudgetState(0, Math.max(0, remainingTokens));
        }
        if (sourceTokens <= remainingTokens) {
            return new LayerBudgetState(length, remainingTokens - sourceTokens);
        }
        return new LayerBudgetState(scaleChars(length, sourceTokens, remainingTokens), 0);
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

    private record LayerBudgetState(int charBudget, int remainingTokens) {
    }
}
