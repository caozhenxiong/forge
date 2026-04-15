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

import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionProperties;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ImplementationToolRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.SubtaskRevisionDirective;
class ImplementationToolPermissionPolicyTests {

    @Test
    void defaultsShellTimeoutFromExecutionPolicy() {
        ImplementationExecutionPolicy executionPolicy = new ImplementationExecutionPolicy();
        ImplementationToolPermissionPolicy policy = new ImplementationToolPermissionPolicy(
                new ImplementationToolPermissionProperties(List.of()),
                executionPolicy
        );
        ImplementationToolPermissionContext context = policy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                DeliveryMode.PATCH,
                false,
                ImplementationToolRegistry.defaultRegistry().toolNames()
        );

        assertEquals(executionPolicy.defaultShellTimeoutMs(), policy.resolveShellTimeout(null, context));
    }

    @Test
    void rejectsShellTimeoutAboveExecutionPolicyMaximum() {
        ImplementationExecutionPolicy executionPolicy = new ImplementationExecutionPolicy();
        ImplementationToolPermissionPolicy policy = new ImplementationToolPermissionPolicy(
                new ImplementationToolPermissionProperties(List.of()),
                executionPolicy
        );
        ImplementationToolPermissionContext context = policy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                DeliveryMode.PATCH,
                false,
                ImplementationToolRegistry.defaultRegistry().toolNames()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.resolveShellTimeout(executionPolicy.maxShellTimeoutMs() + 1L, context)
        );
    }

    @Test
    void repairModeDisablesReadOnlyShellAndWholeRewriteEvenForReworkDelivery() {
        ImplementationExecutionPolicy executionPolicy = new ImplementationExecutionPolicy();
        ImplementationToolPermissionPolicy policy = new ImplementationToolPermissionPolicy(
                new ImplementationToolPermissionProperties(List.of()),
                executionPolicy
        );
        ImplementationToolPermissionContext context = policy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                DeliveryMode.REWORK,
                true,
                ImplementationToolRegistry.defaultRegistry().toolNames()
        );

        assertEquals(DeliveryMode.REWORK, context.deliveryMode());
        assertEquals(true, context.repairMode());
        assertEquals(false, context.allowReadOnlyShell());
        assertEquals(false, context.allowExistingFileWholeRewrite());
    }

    @Test
    void permissionContextConsumesRepairPredicateFromExecutionState() {
        ImplementationExecutionPolicy executionPolicy = new ImplementationExecutionPolicy();
        ImplementationToolPermissionPolicy policy = new ImplementationToolPermissionPolicy(
                new ImplementationToolPermissionProperties(List.of()),
                executionPolicy
        );
        SubtaskExecutionState freshState = new SubtaskExecutionState(DeliveryMode.REWORK, false);
        SubtaskExecutionState repairState = freshState.applyRevisionDirective(
                SubtaskRevisionDirective.patch(List.of(new FileChange("src/app.js", ChangeAction.WRITE, "继续修复")))
        );

        ImplementationToolPermissionContext freshContext = policy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                freshState.deliveryMode(),
                freshState.repairRound(),
                ImplementationToolRegistry.defaultRegistry().toolNames()
        );
        ImplementationToolPermissionContext repairContext = policy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                repairState.deliveryMode(),
                repairState.repairRound(),
                ImplementationToolRegistry.defaultRegistry().toolNames()
        );

        assertEquals(false, freshContext.repairMode());
        assertEquals(true, freshContext.allowExistingFileWholeRewrite());
        assertEquals(true, repairContext.repairMode());
        assertEquals(false, repairContext.allowExistingFileWholeRewrite());
    }
}
