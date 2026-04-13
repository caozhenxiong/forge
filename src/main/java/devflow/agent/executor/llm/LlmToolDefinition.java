package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * provider 暴露给模型的工具定义。
 *
 * <p>这里只保留稳定协议所需字段：
 * 1. 名称；
 * 2. 描述；
 * 3. JSON schema 参数定义。
 */
public record LlmToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {

    public LlmToolDefinition {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }
}
