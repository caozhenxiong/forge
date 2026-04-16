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

import devflow.agent.executor.tools.FileEditTool;
import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ImplementationToolPermissionProperties;
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
import devflow.agent.executor.subtask.ExecutionFileContractMaterializer;

class FileEditToolTests {

    @TempDir
    Path tempDir;

    @Test
    void editToolRejectsWholeFileReplacementOutsideRework() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = true;\n");
        ImplementationToolContext context = newContext(file, DeliveryMode.PATCH, false);

        ToolInvocationResult result = new FileEditTool().invoke(
                new LlmToolCall(
                        "call-edit-rewrite",
                        "Edit",
                        Map.of(
                                "file_path", file.toString(),
                                "old_string", "export const ready = true;\n",
                                "new_string", "export const ready = false;\n"
                        )
                ),
                context
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals(
                "Edit whole-file replacement is only allowed for new files unless the current execution scope explicitly allows whole-file rewrite. Use Read + Edit for existing files in PATCH.",
                payload.get("message")
        );
        assertEquals("WHOLE_FILE_REWRITE_DENIED", payload.get("code"));
        assertEquals("READ_THEN_LOCAL_EDIT", payload.get("requiredAction"));
        assertEquals("export const ready = true;\n", Files.readString(file));
    }

    @Test
    void editToolAllowsTargetedPatchOutsideRework() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = true;\n");
        ImplementationToolContext context = newContext(file, DeliveryMode.PATCH, false);

        ToolInvocationResult result = new FileEditTool().invoke(
                new LlmToolCall(
                        "call-edit-targeted",
                        "Edit",
                        Map.of(
                                "file_path", file.toString(),
                                "old_string", "ready = true",
                                "new_string", "ready = false"
                        )
                ),
                context
        );

        assertTrue(result.success());
        assertEquals("export const ready = false;\n", Files.readString(file));
        assertEquals(1, context.mutationRecords().size());
        assertEquals(Path.of("app.js"), context.mutationRecords().getFirst().relativePath());
    }

    @Test
    void editToolAllowsFirstMaterializationOfExistingEmptyFileInPatchMode() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "");
        ImplementationToolContext context = newContext(file, DeliveryMode.PATCH, false);

        ToolInvocationResult result = new FileEditTool().invoke(
                new LlmToolCall(
                        "call-edit-empty-file",
                        "Edit",
                        Map.of(
                                "file_path", file.toString(),
                                "old_string", "",
                                "new_string", "export const ready = true;\n"
                        )
                ),
                context
        );

        assertTrue(result.success());
        assertEquals("export const ready = true;\n", Files.readString(file));
        assertEquals(1, context.mutationRecords().size());
        assertEquals(Path.of("app.js"), context.mutationRecords().getFirst().relativePath());
    }

    @Test
    void editToolRejectsWholeFileReplacementDuringRepairEvenInReworkMode() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = true;\n");
        ImplementationToolContext context = newContext(file, DeliveryMode.REWORK, true);

        ToolInvocationResult result = new FileEditTool().invoke(
                new LlmToolCall(
                        "call-edit-repair-rewrite",
                        "Edit",
                        Map.of(
                                "file_path", file.toString(),
                                "old_string", "export const ready = true;\n",
                                "new_string", "export const ready = false;\n"
                        )
                ),
                context
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals(
                "Edit whole-file replacement is only allowed for new files unless the current execution scope explicitly allows whole-file rewrite. Use Read + Edit for existing files in repair mode.",
                payload.get("message")
        );
        assertEquals("WHOLE_FILE_REWRITE_DENIED", payload.get("code"));
        assertEquals("READ_THEN_LOCAL_EDIT", payload.get("requiredAction"));
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
                        Set.of("Read", "Edit"),
                        5_000L,
                        5_000L,
                        deliveryMode,
                        repairMode,
                        !repairMode,
                        !repairMode && deliveryMode == DeliveryMode.REWORK
                ),
                new ImplementationToolPermissionPolicy(
                        new ImplementationToolPermissionProperties(List.of("Read", "Edit")),
                        new ImplementationExecutionPolicy()
                ),
                deliveryMode
        );
        context.readFileStateLedger().put(
                file,
                new CoderReadFileState(Files.readString(file), Files.getLastModifiedTime(file).toMillis(), null, null, false)
        );
        return context;
    }
}
