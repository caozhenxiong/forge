package devflow.agent.executor;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImplementationToolPermissionPolicyTests {

    @Test
    void defaultsShellTimeoutFromExecutionPolicy() {
        ImplementationToolPermissionPolicy policy = new ImplementationToolPermissionPolicy();
        ImplementationToolPermissionContext context = policy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                ImplementationToolRegistry.defaultRegistry().toolNames()
        );

        assertEquals(ImplementationExecutionPolicy.defaultShellTimeoutMs(), policy.resolveShellTimeout(null, context));
    }

    @Test
    void rejectsShellTimeoutAboveExecutionPolicyMaximum() {
        ImplementationToolPermissionPolicy policy = new ImplementationToolPermissionPolicy();
        ImplementationToolPermissionContext context = policy.build(
                Path.of("/tmp/project"),
                Set.of(Path.of("src/app.js")),
                ImplementationToolRegistry.defaultRegistry().toolNames()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.resolveShellTimeout(ImplementationExecutionPolicy.maxShellTimeoutMs() + 1L, context)
        );
    }
}
