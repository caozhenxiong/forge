package devflow.agent.executor;

import java.util.Map;

/**
 * implementation coding runtime 的统一工具契约。
 */
interface ImplementationTool {

    ImplementationToolSpecification specification();

    default String name() {
        return specification().name();
    }

    default String description() {
        return specification().description();
    }

    default Map<String, Object> inputSchema() {
        return specification().inputSchema();
    }

    default boolean readOnly() {
        return specification().readOnly();
    }

    default boolean concurrencySafe() {
        return specification().concurrencySafe();
    }

    default int maxResultSizeChars() {
        return specification().maxResultSizeChars();
    }

    ToolInvocationResult invoke(LlmToolCall toolCall, ImplementationToolContext context);

    default LlmToolDefinition toDefinition() {
        return specification().toDefinition();
    }
}
