package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.context.OutputBudgetDecision;
import devflow.agent.executor.llm.ModelRole;

/**
 * 单次模型生成尝试的输入/输出 telemetry。
 *
 * <p>这里记录的是对事件流最关键的几项：
 * 1. 输入 token：估算值 + 模型实际回执值；
 * 2. 输出 token；
 * 3. 本次请求的上下文窗口与输出上限；
 * 4. 最终 done reason。
 */
public record GenerationTelemetry(
        String model,
        String role,
        Integer estimatedPromptTokens,
        Integer actualPromptTokens,
        Integer fixedTokens,
        Integer retrievedTokens,
        Integer outputReserveTokens,
        Integer materialBudgetTokens,
        Integer outputTokens,
        Integer contextWindowTokens,
        Integer reserveTokens,
        Integer availableOutputTokens,
        Integer requestedOutputTokens,
        Integer effectiveOutputTokens,
        String doneReason
) {

    public GenerationTelemetry(
            String model,
            String role,
            Integer estimatedPromptTokens,
            Integer actualPromptTokens,
            Integer outputTokens,
            Integer contextWindowTokens,
            Integer reserveTokens,
            Integer availableOutputTokens,
            Integer requestedOutputTokens,
            Integer effectiveOutputTokens,
            String doneReason
    ) {
        this(
                model,
                role,
                estimatedPromptTokens,
                actualPromptTokens,
                null,
                null,
                null,
                null,
                outputTokens,
                contextWindowTokens,
                reserveTokens,
                availableOutputTokens,
                requestedOutputTokens,
                effectiveOutputTokens,
                doneReason
        );
    }

    public static GenerationTelemetry fromBudget(String model, ModelRole role, OutputBudgetDecision decision) {
        if (decision == null) {
            return new GenerationTelemetry(
                    model,
                    role == null ? "DEFAULT" : role.name(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        return new GenerationTelemetry(
                model,
                role == null ? "DEFAULT" : role.name(),
                decision.estimatedPromptTokens(),
                null,
                decision.fixedTokens(),
                decision.retrievedTokens(),
                decision.outputReserveTokens(),
                decision.materialBudgetTokens(),
                null,
                decision.contextWindowTokens(),
                decision.reserveTokens(),
                decision.availableOutputTokens(),
                decision.requestedOutputTokens(),
                decision.effectiveOutputTokens(),
                null
        );
    }

    public GenerationTelemetry withResponse(Integer promptTokens, Integer outputTokens, String doneReason) {
        return new GenerationTelemetry(
                model,
                role,
                estimatedPromptTokens,
                promptTokens,
                fixedTokens,
                retrievedTokens,
                outputReserveTokens,
                materialBudgetTokens,
                outputTokens,
                contextWindowTokens,
                reserveTokens,
                availableOutputTokens,
                requestedOutputTokens,
                effectiveOutputTokens,
                doneReason
        );
    }
}
