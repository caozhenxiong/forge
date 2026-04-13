package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.time.Duration;

/**
 * 统一维护 Ollama 客户端侧的稳定策略参数。
 *
 * <p>这里集中的是网络连接和空响应重试这类客户端行为，不和模型生成预算混在一起。
 */
public final class OllamaClientPolicy {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_MAX_EMPTY_RESPONSE_RETRIES = 3;
    private static final String CONNECT_TIMEOUT_SECONDS_KEY = "devflow.ollama.connect-timeout-seconds";
    private static final String MAX_EMPTY_RESPONSE_RETRIES_KEY = "devflow.ollama.max-empty-response-retries";

    private OllamaClientPolicy() {
    }

    public static Duration connectTimeout() {
        return Duration.ofSeconds(readPositiveInt(CONNECT_TIMEOUT_SECONDS_KEY, (int) DEFAULT_CONNECT_TIMEOUT.getSeconds()));
    }

    public static int maxEmptyResponseRetries() {
        return readPositiveInt(MAX_EMPTY_RESPONSE_RETRIES_KEY, DEFAULT_MAX_EMPTY_RESPONSE_RETRIES);
    }

    private static int readPositiveInt(String key, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
