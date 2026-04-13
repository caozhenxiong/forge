package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ollama chat/tool-calling 执行器。
 *
 * <p>这里是 Forge 新 coding runtime 的唯一模型入口：
 * 1. text-only 调用和 tool loop 调用共用 `/api/chat`；
 * 2. 预算与 telemetry 仍走统一 OutputBudgetCalculator；
 * 3. provider 侧统一补齐 tool call id，避免上层继续猜测模型返回结构。
 */
final class OllamaChatExecutor {

    private static final String DONE_REASON_LENGTH = "length";
    private final AtomicReference<GenerationTelemetry> lastTelemetry = new AtomicReference<>();

    private final OllamaProperties properties;
    private final OutputBudgetCalculator outputBudgetCalculator;
    private final OllamaTransportClient transportClient;
    private final ObjectMapper objectMapper;

    OllamaChatExecutor(
            OllamaProperties properties,
            OutputBudgetCalculator outputBudgetCalculator,
            OllamaTransportClient transportClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.outputBudgetCalculator = outputBudgetCalculator;
        this.transportClient = transportClient;
        this.objectMapper = objectMapper;
    }

    LlmChatResponse chat(LlmChatRequest request) {
        lastTelemetry.set(null);
        String model = properties.resolveModel(request.role());
        String systemPrompt = flattenSystemMessages(request.messages());
        String nonSystemPrompt = flattenNonSystemMessages(request.messages());
        OutputBudgetDecision budgetDecision = outputBudgetCalculator.calculateOutputBudget(
                model,
                systemPrompt,
                nonSystemPrompt,
                request.options()
        );
        lastTelemetry.set(GenerationTelemetry.fromBudget(model, request.role(), budgetDecision));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", serializeMessages(request.messages()));
        payload.put("stream", false);
        if (request.tools() != null && !request.tools().isEmpty()) {
            payload.put("tools", serializeTools(request.tools()));
        }
        payload.put("options", budgetDecision.effectiveOptions());

        OllamaChatEnvelope lastResponse = null;
        for (int attempt = 1; attempt <= OllamaClientPolicy.maxEmptyResponseRetries(); attempt++) {
            OllamaChatEnvelope response = transportClient.post("/api/chat", payload, OllamaChatEnvelope.class);
            lastResponse = response;
            outputBudgetCalculator.observePromptUsage(
                    model,
                    systemPrompt,
                    nonSystemPrompt,
                    response.promptEvalCount()
            );
            GenerationTelemetry telemetry = GenerationTelemetry.fromBudget(model, request.role(), budgetDecision)
                    .withResponse(response.promptEvalCount(), response.evalCount(), response.doneReason());
            lastTelemetry.set(telemetry);
            LlmChatResponse mapped = toChatResponse(response, telemetry);
            if (!mapped.content().isBlank() || !mapped.toolCalls().isEmpty()) {
                return mapped;
            }
            if (DONE_REASON_LENGTH.equalsIgnoreCase(response.doneReason())) {
                throw new LlmInvocationException(
                        LlmFailureReason.OUTPUT_TRUNCATED,
                        "Ollama chat returned truncated but unusable content for model %s on attempt %d (done=%s, done_reason=%s, eval_count=%s)"
                                .formatted(model, attempt, response.done(), response.doneReason(), response.evalCount())
                );
            }
        }
        throw new LlmInvocationException(
                LlmFailureReason.EMPTY_RESPONSE,
                "Ollama chat returned unusable content for model %s after %d attempts (done=%s, done_reason=%s, eval_count=%s)"
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

    private LlmChatResponse toChatResponse(OllamaChatEnvelope response, GenerationTelemetry telemetry) {
        if (response == null || response.message() == null) {
            return new LlmChatResponse("", List.of(), telemetry, "");
        }
        List<LlmToolCall> toolCalls = new ArrayList<>();
        List<OllamaToolCall> rawToolCalls = response.message().toolCalls();
        if (rawToolCalls != null) {
            int index = 0;
            for (OllamaToolCall rawToolCall : rawToolCalls) {
                if (rawToolCall == null || rawToolCall.function() == null) {
                    continue;
                }
                String generatedId = "tool-" + UUID.randomUUID() + "-" + index++;
                toolCalls.add(new LlmToolCall(
                        generatedId,
                        nullToEmpty(rawToolCall.function().name()),
                        normalizeArguments(rawToolCall.function().arguments())
                ));
            }
        }
        return new LlmChatResponse(
                nullToEmpty(response.message().content()).strip(),
                toolCalls,
                telemetry,
                nullToEmpty(response.doneReason())
        );
    }

    private List<Map<String, Object>> serializeMessages(List<LlmChatMessage> messages) {
        List<Map<String, Object>> payload = new ArrayList<>();
        if (messages == null) {
            return payload;
        }
        for (LlmChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role().wireValue());
            if (message.role() == LlmChatRole.ASSISTANT && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                item.put("tool_calls", serializeAssistantToolCalls(message.toolCalls()));
                if (!message.content().isBlank()) {
                    item.put("content", message.content());
                }
            } else if (message.role() == LlmChatRole.TOOL) {
                item.put("tool_name", message.toolName());
                item.put("content", message.content());
            } else {
                item.put("content", message.content());
            }
            payload.add(item);
        }
        return payload;
    }

    private List<Map<String, Object>> serializeAssistantToolCalls(List<LlmToolCall> toolCalls) {
        List<Map<String, Object>> payload = new ArrayList<>();
        int index = 0;
        for (LlmToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("index", index++);
            function.put("name", toolCall.name());
            function.put("arguments", toolCall.arguments());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "function");
            item.put("function", function);
            payload.add(item);
        }
        return payload;
    }

    private List<Map<String, Object>> serializeTools(List<LlmToolDefinition> tools) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (LlmToolDefinition tool : tools) {
            if (tool == null) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameters());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "function");
            item.put("function", function);
            payload.add(item);
        }
        return payload;
    }

    private Map<String, Object> normalizeArguments(JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isNull()) {
            return Map.of();
        }
        if (argumentsNode.isObject()) {
            return objectMapper.convertValue(argumentsNode, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        }
        if (argumentsNode.isTextual()) {
            String raw = argumentsNode.asText();
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            try {
                JsonNode parsed = objectMapper.readTree(raw);
                if (parsed != null && parsed.isObject()) {
                    return objectMapper.convertValue(parsed, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
                }
            } catch (Exception ignored) {
                return Map.of("raw", raw);
            }
            return Map.of("raw", raw);
        }
        return Map.of();
    }

    private String flattenSystemMessages(List<LlmChatMessage> messages) {
        return flatten(messages, true);
    }

    private String flattenNonSystemMessages(List<LlmChatMessage> messages) {
        return flatten(messages, false);
    }

    private String flatten(List<LlmChatMessage> messages, boolean systemOnly) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (LlmChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            if (systemOnly != (message.role() == LlmChatRole.SYSTEM)) {
                continue;
            }
            StringBuilder builder = new StringBuilder();
            builder.append(message.role().wireValue()).append(": ").append(nullToEmpty(message.content()));
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                for (LlmToolCall toolCall : message.toolCalls()) {
                    builder.append("\n- tool_call=").append(toolCall.name()).append(" ").append(toolCall.arguments());
                }
            }
            parts.add(builder.toString().trim());
        }
        return String.join("\n\n", parts);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record OllamaChatEnvelope(
            OllamaMessage message,
            Boolean done,
            @JsonProperty("done_reason") String doneReason,
            @JsonProperty("eval_count") Integer evalCount,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount
    ) {
    }

    private record OllamaMessage(
            String role,
            String content,
            @JsonProperty("tool_calls") List<OllamaToolCall> toolCalls
    ) {
    }

    private record OllamaToolCall(
            String type,
            OllamaFunction function
    ) {
    }

    private record OllamaFunction(
            Integer index,
            String name,
            JsonNode arguments
    ) {
    }
}
