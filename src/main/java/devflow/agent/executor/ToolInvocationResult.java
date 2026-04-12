package devflow.agent.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 单次工具调用的统一结果。
 *
 * <p>tool runtime 只认 success/failure + payload 两个轴：
 * payload 统一由 ObjectMapper 渲染给模型，不再让每个工具自己拼一套 JSON 字符串。
 */
record ToolInvocationResult(
        boolean success,
        Object payload
) {

    static ToolInvocationResult success(Object payload) {
        return new ToolInvocationResult(true, payload);
    }

    static ToolInvocationResult failure(Object payload) {
        return new ToolInvocationResult(false, payload);
    }

    String render(ObjectMapper objectMapper) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String value) {
            return value;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to render tool payload", exception);
        }
    }
}
