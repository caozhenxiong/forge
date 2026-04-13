package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.ImplementationExecutionPolicy;
import devflow.agent.executor.shell.ShellCommandAnalyzer;
import devflow.agent.executor.shell.ShellCommandDecision;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
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

    private static final String ALLOWED_TOOLS_KEY = "devflow.implementation.allowed-tools";

    public ImplementationToolPermissionPolicy() {
    }

    public ImplementationToolPermissionContext build(
            Path projectPath,
            Set<Path> ownedPaths,
            Collection<String> registeredToolNames
    ) {
        return new ImplementationToolPermissionContext(
                projectPath,
                ownedPaths,
                resolveAllowedToolNames(registeredToolNames),
                ImplementationExecutionPolicy.defaultShellTimeoutMs(),
                ImplementationExecutionPolicy.maxShellTimeoutMs()
        );
    }

    public boolean isToolVisible(ImplementationToolSpecification specification, ImplementationToolPermissionContext context) {
        if (specification == null || context == null || !context.allowsTool(specification.name())) {
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
        String configured = System.getProperty(ALLOWED_TOOLS_KEY);
        if (configured == null || configured.isBlank()) {
            return Set.copyOf(defaults);
        }
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (String raw : configured.split(",")) {
            String candidate = raw == null ? "" : raw.trim();
            if (!candidate.isBlank() && defaults.contains(candidate)) {
                requested.add(candidate);
            }
        }
        return requested.isEmpty() ? Set.copyOf(defaults) : Set.copyOf(requested);
    }
}
