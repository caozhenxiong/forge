package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OllamaLlmProvider implements LlmProvider {

    private static final int MAX_EMPTY_RESPONSE_RETRIES = 3;

    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaLlmProvider(OllamaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
        return generate(systemPrompt, userPrompt, options, null);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
        String model = properties.resolveModel(role);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("system", systemPrompt);
        payload.put("prompt", userPrompt);
        payload.put("stream", false);
        payload.put("options", options == null ? Map.of("num_predict", 1200) : options);
        GenerateResponse lastResponse = null;
        for (int attempt = 1; attempt <= MAX_EMPTY_RESPONSE_RETRIES; attempt++) {
            GenerateResponse response = post("/api/generate", payload, GenerateResponse.class);
            lastResponse = response;
            String content = response.response();
            if (content != null && !content.isBlank()) {
                if ("length".equalsIgnoreCase(nullToEmpty(response.doneReason()))) {
                    continue;
                }
                return content.strip();
            }
        }
        throw new IllegalStateException("Ollama returned unusable content for model %s after %d attempts (done=%s, done_reason=%s, eval_count=%s)"
                .formatted(
                        model,
                        MAX_EMPTY_RESPONSE_RETRIES,
                        lastResponse == null ? "unknown" : lastResponse.done(),
                        lastResponse == null ? "unknown" : lastResponse.doneReason(),
                        lastResponse == null ? "unknown" : lastResponse.evalCount()
                ));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
        return review(systemPrompt, candidateContent, options, null);
    }

    @Override
    public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
        String prompt = """
                请对下面的内容做审阅，必须只返回一个 JSON 对象，格式如下：
                {
                  "decision": "APPROVED|REVISION_REQUIRED|REJECTED",
                  "fixMode": "NONE|PATCH|REWORK",
                  "summary": "不超过60字",
                  "changeRequest": "不超过120字，无则返回空字符串",
                  "evidence": "不超过160字，写清关键证据，无则返回空字符串",
                  "actionItems": "不超过200字，给 coder 可直接执行的动作，无则返回空字符串"
                }

                约束：
                1. 只能返回 JSON，不要 markdown，不要 ```json
                2. 内容必须简短，避免长段落
                3. 如果通过，fixMode 必须为 NONE，changeRequest 必须为空字符串
                4. 如果不通过，fixMode 必须明确选择 PATCH 或 REWORK
                5. PATCH 表示只做局部修补，REWORK 表示允许较大范围重构
                6. evidence 必须写清支持结论的代码/测试/自检证据
                7. actionItems 必须是可执行动作，优先写文件、函数、模块、验证步骤

                待审阅内容：
                %s
                """.formatted(candidateContent);
        Map<String, Object> mergedOptions = new LinkedHashMap<>();
        mergedOptions.put("num_predict", 240);
        if (options != null) {
            mergedOptions.putAll(options);
        }
        String content = generate(systemPrompt, prompt, mergedOptions, role);
        try {
            ReviewPayload payload = objectMapper.readValue(extractJsonObject(content), ReviewPayload.class);
            return new ReviewResult(
                    ReviewDecision.valueOf(payload.decision()),
                    payload.fixMode() == null ? defaultFixMode(ReviewDecision.valueOf(payload.decision())) : FixMode.valueOf(payload.fixMode()),
                    payload.summary(),
                    payload.changeRequest(),
                    payload.evidence(),
                    payload.actionItems()
            );
        } catch (Exception exception) {
            ReviewDecision fallbackDecision = extractDecision(content);
            if (fallbackDecision != null) {
                return new ReviewResult(
                        fallbackDecision,
                        defaultFixMode(fallbackDecision),
                        sanitizeFallback(content),
                        fallbackDecision == ReviewDecision.APPROVED ? "" : "请根据审阅意见修订后重试。",
                        "",
                        fallbackDecision == ReviewDecision.APPROVED ? "" : "先定位 review 中提到的直接问题，再做最小修复。"
                );
            }
            throw new IllegalStateException("Failed to parse review payload from Ollama: " + content, exception);
        }
    }

    private <T> T post(String path, Map<String, Object> payload, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.host() + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Ollama request failed: HTTP " + response.statusCode() + " " + response.body());
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call Ollama at " + properties.host(), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call Ollama at " + properties.host(), exception);
        }
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private ReviewDecision extractDecision(String content) {
        for (ReviewDecision value : ReviewDecision.values()) {
            if (content.contains(value.name())) {
                return value;
            }
        }
        return null;
    }

    private String sanitizeFallback(String content) {
        String singleLine = content.replace("```json", "")
                .replace("```", "")
                .replaceAll("\\s+", " ")
                .trim();
        return singleLine.length() > 120 ? singleLine.substring(0, 120) : singleLine;
    }

    private FixMode defaultFixMode(ReviewDecision decision) {
        return decision == ReviewDecision.APPROVED ? FixMode.NONE : FixMode.PATCH;
    }

    private record GenerateResponse(
            String response,
            Boolean done,
            @JsonProperty("done_reason") String doneReason,
            @JsonProperty("eval_count") Integer evalCount
    ) {
    }

    private record ReviewPayload(
            @JsonProperty("decision") String decision,
            @JsonProperty("fixMode") String fixMode,
            @JsonProperty("summary") String summary,
            @JsonProperty("changeRequest") String changeRequest,
            @JsonProperty("evidence") String evidence,
            @JsonProperty("actionItems") String actionItems
    ) {
    }
}
