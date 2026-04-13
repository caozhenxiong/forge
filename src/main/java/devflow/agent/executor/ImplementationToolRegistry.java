package devflow.agent.executor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * implementation 编码主链的唯一 tool catalog。
 *
 * <p>执行器只从 registry 读取：
 * 1. 当前有哪些工具；
 * 2. 每个工具的静态 contract；
 * 3. 在当前 permission context 下哪些工具可见。
 */
final class ImplementationToolRegistry {

    private final List<ImplementationTool> tools;
    private final Map<String, ImplementationTool> toolsByName;

    ImplementationToolRegistry(List<ImplementationTool> tools) {
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        LinkedHashMap<String, ImplementationTool> mapped = new LinkedHashMap<>();
        for (ImplementationTool tool : this.tools) {
            if (tool != null && !tool.name().isBlank()) {
                mapped.put(tool.name(), tool);
            }
        }
        this.toolsByName = Map.copyOf(mapped);
    }

    static ImplementationToolRegistry defaultRegistry() {
        return new ImplementationToolRegistry(List.of(
                new FileReadTool(),
                new FileEditTool(),
                new FileWriteTool(),
                new FileDeleteTool(),
                new GlobTool(),
                new GrepTool(),
                new BashTool()
        ));
    }

    Set<String> toolNames() {
        return toolsByName.keySet();
    }

    boolean hasTool(String name) {
        return name != null && toolsByName.containsKey(name);
    }

    ImplementationTool tool(String name) {
        return name == null ? null : toolsByName.get(name);
    }

    List<ImplementationTool> visibleTools(
            ImplementationToolPermissionContext permissionContext,
            ImplementationToolPermissionPolicy permissionPolicy
    ) {
        return tools.stream()
                .filter(tool -> tool != null)
                .filter(tool -> permissionPolicy.isToolVisible(tool.specification(), permissionContext))
                .toList();
    }

    List<LlmToolDefinition> toolDefinitions(
            ImplementationToolPermissionContext permissionContext,
            ImplementationToolPermissionPolicy permissionPolicy
    ) {
        return visibleTools(permissionContext, permissionPolicy).stream()
                .map(tool -> tool.specification().toDefinition())
                .toList();
    }

    ImplementationTool resolveVisibleTool(
            String name,
            ImplementationToolPermissionContext permissionContext,
            ImplementationToolPermissionPolicy permissionPolicy
    ) {
        ImplementationTool tool = toolsByName.get(name);
        if (tool == null || !permissionPolicy.isToolVisible(tool.specification(), permissionContext)) {
            return null;
        }
        return tool;
    }
}
