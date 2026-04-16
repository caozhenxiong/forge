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

import devflow.agent.executor.tools.FileWriteTool;
import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionProperties;
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

import devflow.agent.executor.subtask.ExecutionFileContractMaterializer;
import devflow.agent.executor.implementation.toolloop.CoderReadFileState;
import devflow.agent.executor.implementation.toolloop.ImplementationToolContext;
import devflow.agent.executor.implementation.toolloop.ImplementationToolSessionState;
class FileWriteToolTests {

    @TempDir
    Path tempDir;

    @Test
    void writeToolRejectsNoOpOverwrite() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = true;\n");
        ImplementationToolContext context = newContext(file, DeliveryMode.REWORK, false);

        ToolInvocationResult result = new FileWriteTool().invoke(
                new LlmToolCall(
                        "call-1",
                        "Write",
                        Map.of(
                                "file_path", file.toString(),
                                "content", "export const ready = true;\n"
                        )
                ),
                context
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("NO_MATERIAL_CHANGE", payload.get("code"));
        assertEquals(0, context.mutationRecords().size());
    }

    @Test
    void writeToolRejectsExistingFileOverwriteOutsideRework() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = true;\n");
        ImplementationToolContext context = newContext(file, DeliveryMode.PATCH, false);

        ToolInvocationResult result = new FileWriteTool().invoke(
                new LlmToolCall(
                        "call-2",
                        "Write",
                        Map.of(
                                "file_path", file.toString(),
                                "content", "export const ready = false;\n"
                        )
                ),
                context
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals("Write is only allowed for new files unless the current execution scope explicitly allows whole-file rewrite. Use Read + Edit for existing files in PATCH.", payload.get("message"));
        assertEquals("WHOLE_FILE_REWRITE_DENIED", payload.get("code"));
        assertEquals("READ_THEN_LOCAL_EDIT", payload.get("requiredAction"));
        assertEquals("export const ready = true;\n", Files.readString(file));
    }

    @Test
    void writeToolRejectsExistingFileOverwriteDuringRepairEvenInReworkMode() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = true;\n");
        ImplementationToolContext context = newContext(file, DeliveryMode.REWORK, true);

        ToolInvocationResult result = new FileWriteTool().invoke(
                new LlmToolCall(
                        "call-repair-overwrite",
                        "Write",
                        Map.of(
                                "file_path", file.toString(),
                                "content", "export const ready = false;\n"
                        )
                ),
                context
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals(
                "Write is only allowed for new files unless the current execution scope explicitly allows whole-file rewrite. Use Read + Edit for existing files in repair mode.",
                payload.get("message")
        );
        assertEquals("WHOLE_FILE_REWRITE_DENIED", payload.get("code"));
        assertEquals("READ_THEN_LOCAL_EDIT", payload.get("requiredAction"));
    }

    @Test
    void writeToolAllowsRepeatedWholeFileWritesForCreateNewContractWithinSameAttempt() throws Exception {
        Path file = tempDir.resolve("app.js");
        ImplementationToolContext context = newContext(file, DeliveryMode.PATCH, false);
        FileWriteTool tool = new FileWriteTool();

        ToolInvocationResult first = tool.invoke(
                new LlmToolCall(
                        "call-create-first",
                        "Write",
                        Map.of(
                                "file_path", file.toString(),
                                "content", "export const ready = false;\n"
                        )
                ),
                context
        );
        ToolInvocationResult second = tool.invoke(
                new LlmToolCall(
                        "call-create-second",
                        "Write",
                        Map.of(
                                "file_path", file.toString(),
                                "content", "export const ready = true;\n"
                        )
                ),
                context
        );

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals("export const ready = true;\n", Files.readString(file));
        assertEquals(2, context.mutationRecords().size());
    }

    @Test
    void writeToolRejectsRecreatingPatchExistingFileAfterDeletion() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = false;\n");
        ImplementationToolContext context = newContext(file, DeliveryMode.PATCH, false);
        Files.delete(file);
        context.clearReadState(file);

        ToolInvocationResult result = new FileWriteTool().invoke(
                new LlmToolCall(
                        "call-recreate-patch-existing",
                        "Write",
                        Map.of(
                                "file_path", file.toString(),
                                "content", "export const ready = true;\n"
                        )
                ),
                context
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals(
                "Write is only allowed for files whose current execution contract is create-new. Current contract: patch-existing.",
                payload.get("message")
        );
    }

    private ImplementationToolContext newContext(Path file, DeliveryMode deliveryMode, boolean repairMode) throws Exception {
        ImplementationToolContext context = new ImplementationToolContext(
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
                        new ExecutionFileContractMaterializer().materialize(
                                tempDir,
                                List.of(new FileChange("app.js", ChangeAction.WRITE, "更新 app.js"))
                        ),
                        Set.of("Read", "Write"),
                        5_000L,
                        5_000L,
                        deliveryMode,
                        repairMode,
                        !repairMode,
                        !repairMode && deliveryMode == DeliveryMode.REWORK
                ),
                new ImplementationToolPermissionPolicy(
                        new ImplementationToolPermissionProperties(List.of("Read", "Write")),
                        new ImplementationExecutionPolicy()
                ),
                deliveryMode
        );
        if (Files.exists(file)) {
            context.readFileStateLedger().put(
                    file,
                    new CoderReadFileState(Files.readString(file), Files.getLastModifiedTime(file).toMillis(), null, null, false)
            );
        }
        return context;
    }
}
