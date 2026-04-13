package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.ModelBudgetProfile;
import devflow.agent.executor.llm.ModelBudgetRegistry;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 统一计算“这次请求真正还能给模型多少输出预算”。
 *
 * <p>预算语言和 `ContextBudgetPlanner` 保持一致：
 * `C_fixed + C_retrieved + C_out <= W`
 *
 * <p>当前实现映射为：
 * 1. `C_fixed = systemPrompt`
 * 2. `C_retrieved = userPrompt`
 * 3. `C_out = reserveTokens + minimumOutputTokens`
 *
 * <p>调用方给出的 `num_predict` 或 `outputBudgetRatio` 只是业务层理想值；
 * 真正发请求前，还必须结合上下文窗口和分层预算再裁一次。
 */
@Component
public class OutputBudgetCalculator {

    private final ModelBudgetRegistry modelBudgetRegistry;
    private final PromptTokenEstimator promptTokenEstimator;

    public OutputBudgetCalculator(
            ModelBudgetRegistry modelBudgetRegistry,
            PromptTokenEstimator promptTokenEstimator
    ) {
        this.modelBudgetRegistry = modelBudgetRegistry;
        this.promptTokenEstimator = promptTokenEstimator;
    }

    public Map<String, Object> applyOutputBudget(
            String modelName,
            String systemPrompt,
            String userPrompt,
            Map<String, Object> options
    ) {
        return calculateOutputBudget(modelName, systemPrompt, userPrompt, options).effectiveOptions();
    }

    public OutputBudgetDecision calculateOutputBudget(
            String modelName,
            String systemPrompt,
            String userPrompt,
            Map<String, Object> options
    ) {
        ModelBudgetProfile budgetProfile = modelBudgetRegistry.resolve(modelName);
        int fixedTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, systemPrompt);
        int retrievedTokens = promptTokenEstimator.estimateTokens(modelName, budgetProfile, userPrompt);
        int promptTokens = fixedTokens + retrievedTokens;
        int reserveTokens = budgetProfile.reserveTokens();
        int outputReserveTokens = Math.max(
                budgetProfile.minimumOutputTokens(),
                Math.min(
                        budgetProfile.contextWindowTokens() - 1,
                        reserveTokens + budgetProfile.minimumOutputTokens()
                )
        );
        int materialBudgetTokens = Math.max(
                1,
                budgetProfile.contextWindowTokens() - outputReserveTokens - fixedTokens
        );
        int availableTokens = Math.max(
                budgetProfile.minimumOutputTokens(),
                budgetProfile.contextWindowTokens() - promptTokens - reserveTokens
        );
        int budgetCeiling = budgetProfile.safeOutputCeilingTokens(availableTokens);
        int requested = resolveRequestedOutputTokens(options, budgetProfile.minimumOutputTokens(), budgetCeiling);
        int effective = Math.max(
                budgetProfile.minimumOutputTokens(),
                Math.min(requested, budgetCeiling)
        );
        Map<String, Object> withContextWindow = LlmOptions.withNumCtx(options, budgetProfile.contextWindowTokens());
        return new OutputBudgetDecision(
                LlmOptions.withNumPredict(withContextWindow, effective),
                requested,
                effective,
                budgetProfile.contextWindowTokens(),
                promptTokens,
                fixedTokens,
                retrievedTokens,
                outputReserveTokens,
                materialBudgetTokens,
                reserveTokens,
                availableTokens
        );
    }

    public void observePromptUsage(
            String modelName,
            String systemPrompt,
            String userPrompt,
            Integer promptEvalCount
    ) {
        promptTokenEstimator.observePromptUsage(modelName, systemPrompt, userPrompt, promptEvalCount);
    }

    private int resolveRequestedOutputTokens(
            Map<String, Object> options,
            int minimumOutputTokens,
            int budgetCeiling
    ) {
        java.util.OptionalDouble ratio = LlmOptions.readOutputBudgetRatio(options);
        java.util.OptionalInt requestedCap = LlmOptions.readNumPredict(options);
        if (ratio.isPresent()) {
            double normalizedRatio = Math.max(0.0d, Math.min(1.0d, ratio.getAsDouble()));
            int scaled = (int) Math.round(budgetCeiling * normalizedRatio);
            int ratioBudget = Math.max(minimumOutputTokens, Math.min(budgetCeiling, scaled));
            if (requestedCap.isPresent()) {
                return Math.max(
                        minimumOutputTokens,
                        Math.min(ratioBudget, requestedCap.getAsInt())
                );
            }
            return ratioBudget;
        }
        if (requestedCap.isPresent()) {
            return Math.max(minimumOutputTokens, requestedCap.getAsInt());
        }
        return budgetCeiling;
    }
}
