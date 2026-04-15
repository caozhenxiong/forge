package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import devflow.agent.executor.tools.ImplementationTool;
import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionProperties;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ImplementationToolRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationToolRegistryTests {

    private final ImplementationToolRegistry registry = ImplementationToolRegistry.defaultRegistry();
    private final ImplementationToolPermissionPolicy permissionPolicy = new ImplementationToolPermissionPolicy(
            new ImplementationToolPermissionProperties(List.of()),
            new ImplementationExecutionPolicy()
    );

    @Test
    void hidesWorkspaceMutationToolsWhenSubtaskOwnsNoPaths() {
        ImplementationToolPermissionContext context = permissionPolicy.build(
                Path.of("/tmp/project"),
                Set.of(),
                DeliveryMode.PATCH,
                false,
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
                DeliveryMode.PATCH,
                false,
                registry.toolNames()
        );

        Set<String> visible = registry.visibleTools(context, permissionPolicy).stream()
                .map(ImplementationTool::name)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(visible.contains("Edit"));
        assertTrue(visible.contains("Write"));
        assertTrue(visible.contains("Delete"));
    }

    @Test
    void hidesBashDuringRepairMode() {
        ImplementationToolPermissionContext context = permissionPolicy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                DeliveryMode.PATCH,
                true,
                registry.toolNames()
        );

        Set<String> visible = registry.visibleTools(context, permissionPolicy).stream()
                .map(ImplementationTool::name)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(visible.contains("Read"));
        assertTrue(visible.contains("Edit"));
        assertTrue(visible.contains("Write"));
        assertTrue(visible.contains("Delete"));
        assertFalse(visible.contains("Bash"));
    }
}
