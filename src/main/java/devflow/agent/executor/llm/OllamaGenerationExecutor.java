package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.context.CompactedPrompt;
import devflow.agent.executor.context.ContextCompactor;
import devflow.agent.executor.context.OutputBudgetCalculator;
import devflow.agent.executor.context.OutputBudgetDecision;
import devflow.agent.executor.generation.GenerationTelemetry;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ollama 文本生成执行器。
 *
 * <p>负责：
 * 1. compact + output budget 前置；
 * 2. 统一的 generate 重试与截断失败；
 * 3. prompt usage 回填校准。
 */
final class OllamaGenerationExecutor {

    private static final String DONE_REASON_LENGTH = "length";
    private final AtomicReference<GenerationTelemetry> lastTelemetry = new AtomicReference<>();

    private final OllamaProperties properties;
    private final OutputBudgetCalculator outputBudgetCalculator;
    private final ContextCompactor contextCompactor;
    private final OllamaTransportClient transportClient;

    OllamaGenerationExecutor(
            OllamaProperties properties,
            OutputBudgetCalculator outputBudgetCalculator,
            ContextCompactor contextCompactor,
            OllamaTransportClient transportClient
    ) {
        this.properties = properties;
        this.outputBudgetCalculator = outputBudgetCalculator;
        this.contextCompactor = contextCompactor;
        this.transportClient = transportClient;
    }

    String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
        lastTelemetry.set(null);
        String model = properties.resolveModel(role);
        CompactedPrompt compactedPrompt = contextCompactor.compact(model, systemPrompt, userPrompt);
        OutputBudgetDecision budgetDecision = outputBudgetCalculator.calculateOutputBudget(
                model,
                compactedPrompt.systemPrompt(),
                compactedPrompt.userPrompt(),
                options
        );
        lastTelemetry.set(GenerationTelemetry.fromBudget(model, role, budgetDecision));
        Map<String, Object> effectiveOptions = budgetDecision.effectiveOptions();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("system", compactedPrompt.systemPrompt());
        payload.put("prompt", compactedPrompt.userPrompt());
        payload.put("stream", false);
        payload.put("options", effectiveOptions);
        OllamaGenerateResponse lastResponse = null;
        for (int attempt = 1; attempt <= OllamaClientPolicy.maxEmptyResponseRetries(); attempt++) {
            OllamaGenerateResponse response = transportClient.post("/api/generate", payload, OllamaGenerateResponse.class);
            lastResponse = response;
            outputBudgetCalculator.observePromptUsage(
                    model,
                    compactedPrompt.systemPrompt(),
                    compactedPrompt.userPrompt(),
                    response.promptEvalCount()
            );
            lastTelemetry.set(
                    GenerationTelemetry.fromBudget(model, role, budgetDecision)
                            .withResponse(response.promptEvalCount(), response.evalCount(), response.doneReason())
            );
            String content = response.response();
            if (content != null && !content.isBlank()) {
                if (DONE_REASON_LENGTH.equalsIgnoreCase(nullToEmpty(response.doneReason()))) {
                    throw new LlmInvocationException(
                            LlmFailureReason.OUTPUT_TRUNCATED,
                            "Ollama returned truncated content for model %s on attempt %d (done=%s, done_reason=%s, eval_count=%s)"
                                    .formatted(
                                            model,
                                            attempt,
                                            response.done(),
                                            response.doneReason(),
                                            response.evalCount()
                                    )
                    );
                }
                return content.strip();
            }
        }
        throw new LlmInvocationException(
                LlmFailureReason.EMPTY_RESPONSE,
                "Ollama returned unusable content for model %s after %d attempts (done=%s, done_reason=%s, eval_count=%s)"
                        .formatted(
                                model,
                                OllamaClientPolicy.maxEmptyResponseRetries(),
                                lastResponse == null ? "unknown" : lastResponse.done(),
                                lastResponse == null ? "unknown" : lastResponse.doneReason(),
                                lastResponse == null ? "unknown" : lastResponse.evalCount()
                        )
        );
    }

    GenerationTelemetry consumeLastTelemetry() {
        return lastTelemetry.getAndSet(null);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record OllamaGenerateResponse(
            String response,
            Boolean done,
            @JsonProperty("done_reason") String doneReason,
            @JsonProperty("eval_count") Integer evalCount,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount
    ) {
    }
}
