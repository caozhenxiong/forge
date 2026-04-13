package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ImplementationTool;
import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ImplementationToolRegistry;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationToolRegistryTests {

    private final ImplementationToolRegistry registry = ImplementationToolRegistry.defaultRegistry();
    private final ImplementationToolPermissionPolicy permissionPolicy = new ImplementationToolPermissionPolicy();

    @Test
    void hidesWorkspaceMutationToolsWhenSubtaskOwnsNoPaths() {
        ImplementationToolPermissionContext context = permissionPolicy.build(
                Path.of("/tmp/project"),
                Set.of(),
                registry.toolNames()
        );

        Set<String> visible = registry.visibleTools(context, permissionPolicy).stream()
                .map(ImplementationTool::name)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(visible.contains("Read"));
        assertTrue(visible.contains("Glob"));
        assertTrue(visible.contains("Grep"));
        assertTrue(visible.contains("Bash"));
        assertFalse(visible.contains("Edit"));
        assertFalse(visible.contains("Write"));
        assertFalse(visible.contains("Delete"));
    }

    @Test
    void keepsWorkspaceMutationToolsWhenSubtaskOwnsPaths() {
        ImplementationToolPermissionContext context = permissionPolicy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                registry.toolNames()
        );

        Set<String> visible = registry.visibleTools(context, permissionPolicy).stream()
                .map(ImplementationTool::name)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(visible.contains("Edit"));
        assertTrue(visible.contains("Write"));
        assertTrue(visible.contains("Delete"));
    }
}
