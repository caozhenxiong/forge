package devflow.agent.executor.implementation.toolloop;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.domain.RunRecord;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.implementation.ImplementationExecutionPolicy;
import devflow.agent.executor.tools.BashTool;
import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ImplementationToolPermissionProperties;
import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolInvocationResult;
import devflow.agent.executor.llm.LlmToolCall;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BashToolFailureDiagnosticsTests {

    @TempDir
    Path tempDir;

    @Test
    void deniedWriteRecordsScopeViolationDiagnostic() {
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of());

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-denied-write",
                        "Bash",
                        Map.of("command", "printf 'hello' > app.js")
                ),
                context
        );

        assertFalse(result.success());
        assertEquals(1, context.diagnostics().size());
        ImplementationDiagnosticRecord diagnostic = context.diagnostics().getFirst();
        assertEquals(Path.of("app.js"), diagnostic.relativePath());
        assertEquals(ToolLoopDiagnosticStatus.FAILED, diagnostic.status());
        assertEquals(ImplementationDiagnosticSource.TOOL_FAILURE, diagnostic.source());
        assertEquals(ToolFailureCode.TARGET_SCOPE_VIOLATION, diagnostic.failureCode());
        org.junit.jupiter.api.Assertions.assertTrue(diagnostic.evidence().contains("printf 'hello' > app.js"));
    }

    @Test
    void unsupportedCommandMayStillRecordEmptyDiagnosticPath() {
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of(Path.of("app.js")));

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-unsupported-command",
                        "Bash",
                        Map.of("command", "python app.py")
                ),
                context
        );

        assertFalse(result.success());
        assertEquals(1, context.diagnostics().size());
        ImplementationDiagnosticRecord diagnostic = context.diagnostics().getFirst();
        assertEquals(Path.of(""), diagnostic.relativePath());
        assertEquals(ToolLoopDiagnosticStatus.FAILED, diagnostic.status());
        assertEquals(ImplementationDiagnosticSource.TOOL_FAILURE, diagnostic.source());
        assertEquals(ToolFailureCode.COMMAND_FAILED, diagnostic.failureCode());
    }

    @Test
    void deniedCpKeepsStructuredTargetPath() throws Exception {
        Files.writeString(tempDir.resolve("source.txt"), "hello\n");
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of());

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-denied-cp",
                        "Bash",
                        Map.of("command", "cp source.txt copied.txt")
                ),
                context
        );

        assertFalse(result.success());
        assertEquals(1, context.diagnostics().size());
        ImplementationDiagnosticRecord diagnostic = context.diagnostics().getFirst();
        assertEquals(Path.of("copied.txt"), diagnostic.relativePath());
        assertEquals(ToolFailureCode.TARGET_SCOPE_VIOLATION, diagnostic.failureCode());
    }

    @Test
    void deniedMkdirKeepsStructuredDirectoryPath() {
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of());

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-denied-mkdir",
                        "Bash",
                        Map.of("command", "mkdir src/generated")
                ),
                context
        );

        assertFalse(result.success());
        assertEquals(1, context.diagnostics().size());
        ImplementationDiagnosticRecord diagnostic = context.diagnostics().getFirst();
        assertEquals(Path.of("src/generated"), diagnostic.relativePath());
        assertEquals(ToolFailureCode.TARGET_SCOPE_VIOLATION, diagnostic.failureCode());
    }

    @Test
    void deniedMultiSegmentWriteKeepsFirstStructuredTargetPath() {
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of(Path.of("one.js"), Path.of("two.js")));

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-multi-write",
                        "Bash",
                        Map.of("command", "touch one.js && touch two.js")
                ),
                context
        );

        assertFalse(result.success());
        assertEquals(1, context.diagnostics().size());
        ImplementationDiagnosticRecord diagnostic = context.diagnostics().getFirst();
        assertEquals(Path.of("one.js"), diagnostic.relativePath());
        assertEquals(ToolFailureCode.COMMAND_FAILED, diagnostic.failureCode());
    }

    @Test
    void preExecutionValidationFailureRecordsCommandFailedDiagnostic() throws Exception {
        Files.writeString(tempDir.resolve("app.js"), "export const ready = true;\n");
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of(Path.of("app.js")));

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-existing-overwrite",
                        "Bash",
                        Map.of("command", "printf 'export const ready = false;\\n' > app.js")
                ),
                context
        );

        assertFalse(result.success());
        assertEquals(1, context.diagnostics().size());
        ImplementationDiagnosticRecord diagnostic = context.diagnostics().getFirst();
        assertEquals(Path.of("app.js"), diagnostic.relativePath());
        assertEquals(ToolLoopDiagnosticStatus.FAILED, diagnostic.status());
        assertEquals(ImplementationDiagnosticSource.TOOL_FAILURE, diagnostic.source());
        assertEquals(ToolFailureCode.COMMAND_FAILED, diagnostic.failureCode());
    }

    private ImplementationToolContext newContext(Set<Path> ownedPaths) {
        return new ImplementationToolContext(
                tempDir,
                new RunRecord(
                        UUID.randomUUID(),
                        tempDir,
                        "goal",
                        "constraints",
                        null,
                        null,
                        null,
                        Map.of(),
                        Instant.now(),
                        Instant.now()
                ),
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                new ImplementationToolSessionState(),
                new ImplementationToolPermissionContext(
                        tempDir,
                        ownedPaths,
                        Set.of("Bash"),
                        5_000L,
                        5_000L
                ),
                new ImplementationToolPermissionPolicy(
                        new ImplementationToolPermissionProperties(List.of("Bash")),
                        new ImplementationExecutionPolicy()
                ),
                DeliveryMode.PATCH,
                List.of()
        );
    }
}
