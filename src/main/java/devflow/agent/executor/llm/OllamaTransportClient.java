package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Ollama 传输客户端。
 *
 * <p>只负责：
 * 1. 发送 HTTP 请求；
 * 2. 统一 transport/timeout/invalid-response 异常归类；
 * 3. 避免 provider/生成器直接拼装网络细节。
 */
final class OllamaTransportClient {

    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    OllamaTransportClient(OllamaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    <T> T post(String path, Map<String, Object> payload, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.host() + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(properties.timeoutSeconds(), TimeUnit.SECONDS)
                    .join();
            if (response.statusCode() >= 400) {
                throw new LlmInvocationException(
                        LlmFailureReason.TRANSPORT,
                        "Ollama request failed: HTTP " + response.statusCode() + " " + response.body()
                );
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof TimeoutException) {
                throw new LlmInvocationException(
                        LlmFailureReason.TIMEOUT,
                        "Ollama request timed out after %d seconds at %s"
                                .formatted(properties.timeoutSeconds(), properties.host()),
                        cause
                );
            }
            if (cause instanceof IOException ioException) {
                throw new LlmInvocationException(
                        LlmFailureReason.TRANSPORT,
                        "Failed to call Ollama at " + properties.host(),
                        ioException
                );
            }
            throw new LlmInvocationException(
                    LlmFailureReason.TRANSPORT,
                    "Failed to call Ollama at " + properties.host(),
                    cause == null ? exception : cause
            );
        } catch (IOException exception) {
            throw new LlmInvocationException(
                    LlmFailureReason.INVALID_RESPONSE,
                    "Failed to call Ollama at " + properties.host(),
                    exception
            );
        }
    }
}
