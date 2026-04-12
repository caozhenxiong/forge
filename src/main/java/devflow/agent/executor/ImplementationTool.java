package devflow.agent.executor;

import java.util.Map;

/**
 * implementation coding runtime 的统一工具契约。
 */
interface ImplementationTool {

    String name();

    String description();

    Map<String, Object> inputSchema();

    boolean readOnly();

    default boolean concurrencySafe() {
        return readOnly();
    }

    int maxResultSizeChars();

    ToolInvocationResult invoke(LlmToolCall toolCall, ImplementationToolContext context);

    default LlmToolDefinition toDefinition() {
        return new LlmToolDefinition(name(), description(), inputSchema());
    }
}
