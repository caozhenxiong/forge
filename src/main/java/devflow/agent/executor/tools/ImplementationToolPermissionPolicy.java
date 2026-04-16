package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.implementation.ImplementationExecutionPolicy;
import devflow.agent.executor.shell.ShellCommandAnalyzer;
import devflow.agent.executor.shell.ShellCommandDecision;
import devflow.agent.executor.subtask.ExecutionFileContractSet;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * implementation tool runtime 的唯一权限与执行策略入口。
 *
 * <p>这层集中维护：
 * 1. 哪些工具当前可见；
 * 2. 哪些路径允许写；
 * 3. shell timeout 的合法边界。
 *
 * <p>工具和执行器不再各自藏一份权限规则。
 */
public final class ImplementationToolPermissionPolicy {

    private final ImplementationToolPermissionProperties properties;
    private final ImplementationExecutionPolicy implementationExecutionPolicy;

    public ImplementationToolPermissionPolicy(
            ImplementationToolPermissionProperties properties,
            ImplementationExecutionPolicy implementationExecutionPolicy
    ) {
        this.properties = properties;
        this.implementationExecutionPolicy = implementationExecutionPolicy;
    }

    public ImplementationToolPermissionContext build(
            Path projectPath,
            ExecutionFileContractSet executionFileContract,
            DeliveryMode deliveryMode,
            boolean repairMode,
            Collection<String> registeredToolNames
    ) {
        DeliveryMode resolvedDeliveryMode = deliveryMode == null ? DeliveryMode.PATCH : deliveryMode;
        return new ImplementationToolPermissionContext(
                projectPath,
                executionFileContract,
                resolveAllowedToolNames(registeredToolNames),
                implementationExecutionPolicy.defaultShellTimeoutMs(),
                implementationExecutionPolicy.maxShellTimeoutMs(),
                resolvedDeliveryMode,
                repairMode,
                !repairMode,
                !repairMode && resolvedDeliveryMode == DeliveryMode.REWORK
        );
    }

    public boolean isToolVisible(ImplementationToolSpecification specification, ImplementationToolPermissionContext context) {
        if (specification == null || context == null || !context.allowsTool(specification.name())) {
            return false;
        }
        if ("Bash".equals(specification.name()) && context.repairMode()) {
            return false;
        }
        return switch (specification.permissionScope()) {
            case WRITE_OWNED_PATHS, DELETE_OWNED_PATHS -> context.hasOwnedPaths();
            case READ_WORKSPACE, SEARCH_WORKSPACE, EXECUTE_SHELL -> true;
        };
    }

    public void assertToolVisible(ImplementationToolSpecification specification, ImplementationToolPermissionContext context) {
        if (!isToolVisible(specification, context)) {
            throw new IllegalArgumentException("Tool is not available in the current subtask scope: " + specification.name());
        }
    }

    public void assertWritablePath(Path relativePath, ImplementationToolPermissionContext context) {
        if (relativePath == null || context == null || !context.ownedPaths().contains(relativePath.normalize())) {
            throw new IllegalArgumentException("Write is only allowed for current owned paths: " + context.ownedPaths());
        }
    }

    public long resolveShellTimeout(Long requestedTimeoutMs, ImplementationToolPermissionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Missing tool permission context.");
        }
        long resolved = requestedTimeoutMs == null || requestedTimeoutMs <= 0
                ? context.defaultShellTimeoutMs()
                : requestedTimeoutMs;
        if (resolved > context.maxShellTimeoutMs()) {
            throw new IllegalArgumentException(
                    "Shell timeout exceeds current execution policy: max=" + context.maxShellTimeoutMs() + "ms"
            );
        }
        return resolved;
    }

    public ShellCommandDecision decideShellCommand(
            String command,
            ImplementationToolPermissionContext context,
            ShellCommandAnalyzer analyzer
    ) {
        if (analyzer == null) {
            throw new IllegalArgumentException("Missing shell command analyzer.");
        }
        return analyzer.analyze(command, context);
    }

    private Set<String> resolveAllowedToolNames(Collection<String> registeredToolNames) {
        LinkedHashSet<String> defaults = new LinkedHashSet<>();
        if (registeredToolNames != null) {
            for (String toolName : registeredToolNames) {
                if (toolName != null && !toolName.isBlank()) {
                    defaults.add(toolName);
                }
            }
        }
        List<String> configured = properties.allowedTools();
        if (configured == null || configured.isEmpty()) {
            return Set.copyOf(defaults);
        }
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (String raw : configured) {
            String candidate = raw == null ? "" : raw.trim();
            if (!candidate.isBlank() && defaults.contains(candidate)) {
                requested.add(candidate);
            }
        }
        return requested.isEmpty() ? Set.copyOf(defaults) : Set.copyOf(requested);
    }
}
