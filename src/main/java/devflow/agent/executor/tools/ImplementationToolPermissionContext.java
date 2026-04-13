package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import java.util.Set;

/**
 * implementation 编码主链的权限上下文。
 *
 * <p>这层只保存当前子任务明确允许的能力边界，不负责策略计算。
 */
public record ImplementationToolPermissionContext(
        Path projectPath,
        Set<Path> ownedPaths,
        Set<String> allowedToolNames,
        long defaultShellTimeoutMs,
        long maxShellTimeoutMs
) {

    public ImplementationToolPermissionContext {
        projectPath = projectPath == null ? Path.of("") : projectPath.toAbsolutePath().normalize();
        ownedPaths = ownedPaths == null ? Set.of() : Set.copyOf(ownedPaths);
        allowedToolNames = allowedToolNames == null ? Set.of() : Set.copyOf(allowedToolNames);
        defaultShellTimeoutMs = Math.max(1L, defaultShellTimeoutMs);
        maxShellTimeoutMs = Math.max(defaultShellTimeoutMs, maxShellTimeoutMs);
    }

    public boolean allowsTool(String toolName) {
        return toolName != null && allowedToolNames.contains(toolName);
    }

    public boolean hasOwnedPaths() {
        return !ownedPaths.isEmpty();
    }
}
