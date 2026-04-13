package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.BashTool;
import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ToolInvocationResult;

import devflow.agent.executor.llm.LlmToolCall;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.domain.RunRecord;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.implementation.toolloop.CoderReadFileState;
import devflow.agent.executor.implementation.toolloop.ImplementationToolContext;
import devflow.agent.executor.implementation.toolloop.ImplementationToolSessionState;
class BashToolTests {

    @TempDir
    Path tempDir;

    @Test
    void bashToolTerminatesBackgroundJobsStartedByCommand() throws Exception {
        BashTool bashTool = new BashTool();
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-1",
                        "Bash",
                        Map.of(
                                "command", "sleep 30 &",
                                "timeout", 5_000
                        )
                ),
                newContext(Set.of())
        );

        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals(0, ((Number) payload.get("exitCode")).intValue());

        Thread.sleep(200L);
        assertFalse(hasProcessContaining("sleep 30"));
    }

    @Test
    void bashToolRejectsWriteOutsideOwnedPaths() {
        BashTool bashTool = new BashTool();
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-2",
                        "Bash",
                        Map.of("command", "printf 'hello' > app.js")
                ),
                newContext(Set.of())
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("SCOPE_VIOLATION", payload.get("reasonCode"));
        assertEquals(Boolean.FALSE, payload.get("executed"));
    }

    @Test
    void bashToolRecordsDeclaredWriteIntoMutationLedger() throws Exception {
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of(Path.of("app.js")));
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-3",
                        "Bash",
                        Map.of("command", "printf 'export const ready = true;\\n' > app.js")
                ),
                context
        );

        assertTrue(result.success());
        assertTrue(Files.readString(tempDir.resolve("app.js")).contains("ready = true"));
        assertEquals(1, context.mutationRecords().size());
        assertEquals(Path.of("app.js"), context.mutationRecords().getFirst().relativePath());
        assertTrue(context.mutationRecords().getFirst().afterExists());
    }

    @Test
    void bashToolAllowsCdBeforeSingleWriteSegment() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of(Path.of("src/app.js")));

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-cd-write",
                        "Bash",
                        Map.of("command", "cd src && printf 'export const ready = true;\\n' > app.js")
                ),
                context
        );

        assertTrue(result.success());
        assertEquals("export const ready = true;\n", Files.readString(tempDir.resolve("src/app.js")));
    }

    @Test
    void bashToolRejectsExistingFileOverwriteOutsideRework() throws Exception {
        Files.writeString(tempDir.resolve("app.js"), "export const ready = true;\n");
        BashTool bashTool = new BashTool();
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-overwrite",
                        "Bash",
                        Map.of("command", "printf 'export const ready = false;\\n' > app.js")
                ),
                newContext(Set.of(Path.of("app.js")))
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("Bash write is only allowed for new files unless the current delivery mode is REWORK. Use Read + Edit for existing files in PATCH.", payload.get("message"));
        assertEquals("export const ready = true;\n", Files.readString(tempDir.resolve("app.js")));
    }

    @Test
    void bashToolRejectsShellStateCommands() {
        BashTool bashTool = new BashTool();
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-shell-state",
                        "Bash",
                        Map.of("command", "export READY=1")
                ),
                newContext(Set.of())
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("UNSUPPORTED_SHELL_STATE_COMMAND", payload.get("reasonCode"));
    }

    @Test
    void bashToolRejectsCopyWhenSourceWasNotRead() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/template.js"), "export const ready = true;\n");
        BashTool bashTool = new BashTool();

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-copy-unread",
                        "Bash",
                        Map.of("command", "cp src/template.js app.js")
                ),
                newContext(Set.of(Path.of("app.js")))
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("PRE_EXEC_VALIDATION_FAILED", payload.get("reasonCode"));
        assertEquals("Bash copy source requires a prior full Read on src/template.js.", payload.get("message"));
    }

    @Test
    void bashToolAllowsCopyWhenSourceWasReadAndTargetIsNewOwnedFile() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/template.js"), "export const ready = true;\n");
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of(Path.of("app.js")));
        Path source = tempDir.resolve("src/template.js");
        context.readFileStateLedger().put(
                source,
                new CoderReadFileState(Files.readString(source), Files.getLastModifiedTime(source).toMillis(), null, null, false)
        );

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-copy-read",
                        "Bash",
                        Map.of("command", "cp src/template.js app.js")
                ),
                context
        );

        assertTrue(result.success());
        assertEquals("export const ready = true;\n", Files.readString(tempDir.resolve("app.js")));
    }

    @Test
    void bashToolRejectsCopyIntoExistingTargetOutsideRework() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/template.js"), "export const ready = true;\n");
        Files.writeString(tempDir.resolve("app.js"), "export const ready = false;\n");
        BashTool bashTool = new BashTool();
        ImplementationToolContext context = newContext(Set.of(Path.of("app.js")));
        Path source = tempDir.resolve("src/template.js");
        context.readFileStateLedger().put(
                source,
                new CoderReadFileState(Files.readString(source), Files.getLastModifiedTime(source).toMillis(), null, null, false)
        );

        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-copy-existing",
                        "Bash",
                        Map.of("command", "cp src/template.js app.js")
                ),
                context
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("PRE_EXEC_VALIDATION_FAILED", payload.get("reasonCode"));
        assertEquals("export const ready = false;\n", Files.readString(tempDir.resolve("app.js")));
    }

    @Test
    void bashToolRejectsFilesystemReadersWritingViaRedirection() {
        BashTool bashTool = new BashTool();
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-4",
                        "Bash",
                        Map.of("command", "cat /etc/passwd > app.js")
                ),
                newContext(Set.of(Path.of("app.js")))
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("UNSAFE_WRITE_SYNTAX", payload.get("reasonCode"));
        assertEquals(Boolean.FALSE, payload.get("executed"));
    }

    @Test
    void bashToolRejectsChainedWriteCommands() {
        BashTool bashTool = new BashTool();
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-5",
                        "Bash",
                        Map.of("command", "printf 'a' > app.js ; printf 'b' > app.js")
                ),
                newContext(Set.of(Path.of("app.js")))
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("UNSAFE_WRITE_SYNTAX", payload.get("reasonCode"));
    }

    @Test
    void bashToolRejectsSedInPlaceEditing() {
        BashTool bashTool = new BashTool();
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-6",
                        "Bash",
                        Map.of("command", "sed -i 's/a/b/' app.js")
                ),
                newContext(Set.of(Path.of("app.js")))
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("UNSUPPORTED_SHELL_COMMAND", payload.get("reasonCode"));
    }

    @Test
    void bashToolRejectsTeeWriteCommands() {
        BashTool bashTool = new BashTool();
        ToolInvocationResult result = bashTool.invoke(
                new LlmToolCall(
                        "call-7",
                        "Bash",
                        Map.of("command", "tee app.js")
                ),
                newContext(Set.of(Path.of("app.js")))
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("SHELL_COMMAND_DENIED", payload.get("code"));
        assertEquals("UNSUPPORTED_SHELL_COMMAND", payload.get("reasonCode"));
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
                new ImplementationToolPermissionPolicy(),
                DeliveryMode.PATCH,
                List.of()
        );
    }

    private boolean hasProcessContaining(String token) {
        return ProcessHandle.allProcesses()
                .map(ProcessHandle::info)
                .map(ProcessHandle.Info::commandLine)
                .flatMap(java.util.Optional::stream)
                .anyMatch(commandLine -> commandLine.contains(token));
    }
}
