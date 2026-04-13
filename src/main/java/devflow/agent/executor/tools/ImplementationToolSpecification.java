package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmToolDefinition;

import java.util.Map;

/**
 * 单个 implementation tool 的完整静态契约。
 *
 * <p>执行器不再自己拼工具定义、并发性和预算信息；
 * 这些都必须从 tool spec 读取。
 */
public record ImplementationToolSpecification(
        String name,
        String description,
        Map<String, Object> inputSchema,
        boolean readOnly,
        boolean concurrencySafe,
        int maxResultSizeChars,
        ImplementationToolPermissionScope permissionScope
) {

    public ImplementationToolSpecification {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        maxResultSizeChars = Math.max(1, maxResultSizeChars);
        permissionScope = permissionScope == null ? ImplementationToolPermissionScope.READ_WORKSPACE : permissionScope;
    }

    public LlmToolDefinition toDefinition() {
        return new LlmToolDefinition(name, description, inputSchema);
    }
}
