package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.subtask.ExecutionFileContractSet;
import java.nio.file.Path;
import java.util.Set;

/**
 * implementation 编码主链的权限上下文。
 *
 * <p>这层只保存当前子任务明确允许的能力边界，不负责策略计算。
 */
public record ImplementationToolPermissionContext(
        Path projectPath,
        ExecutionFileContractSet executionFileContract,
        Set<String> allowedToolNames,
        long defaultShellTimeoutMs,
        long maxShellTimeoutMs,
        DeliveryMode deliveryMode,
        boolean repairMode,
        boolean allowReadOnlyShell,
        boolean allowExistingFileWholeRewrite
) {

    public ImplementationToolPermissionContext {
        projectPath = projectPath == null ? Path.of("") : projectPath.toAbsolutePath().normalize();
        executionFileContract = executionFileContract == null ? ExecutionFileContractSet.empty() : executionFileContract;
        allowedToolNames = allowedToolNames == null ? Set.of() : Set.copyOf(allowedToolNames);
        defaultShellTimeoutMs = Math.max(1L, defaultShellTimeoutMs);
        maxShellTimeoutMs = Math.max(defaultShellTimeoutMs, maxShellTimeoutMs);
        deliveryMode = deliveryMode == null ? DeliveryMode.PATCH : deliveryMode;
    }

    public boolean allowsTool(String toolName) {
        return toolName != null && allowedToolNames.contains(toolName);
    }

    public Set<Path> ownedPaths() {
        return executionFileContract.ownedPaths();
    }

    public boolean hasOwnedPaths() {
        return !executionFileContract.isEmpty();
    }
}
