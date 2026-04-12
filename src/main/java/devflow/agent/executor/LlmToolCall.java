package devflow.agent.executor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型返回的单个工具调用。
 *
 * <p>Ollama 的 tool call 响应没有稳定 id，因此 provider 侧必须在这里补齐统一 id，
 * 让 tool runtime、持久化结果和事件日志共享同一条主键。
 */
public record LlmToolCall(
        String id,
        String name,
        Map<String, Object> arguments
) {

    public LlmToolCall {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
    }
}
