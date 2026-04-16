package devflow.agent.executor;

import devflow.agent.executor.tools.FileDeleteTool;
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

import devflow.agent.executor.implementation.ImplementationExecutionPolicy;
import devflow.agent.executor.implementation.toolloop.ImplementationToolContext;
import devflow.agent.executor.implementation.toolloop.ImplementationToolSessionState;
import devflow.agent.executor.subtask.ExecutionFileContractMaterializer;

class FileDeleteToolTests {

    @TempDir
    Path tempDir;

    @Test
    void deleteToolRejectsDeletingPatchExistingFile() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = true;\n");
        ImplementationToolContext context = newContext(file, ChangeAction.WRITE);

        ToolInvocationResult result = new FileDeleteTool().invoke(
                new LlmToolCall(
                        "call-delete-patch-existing",
                        "Delete",
                        Map.of("file_path", file.toString())
                ),
                context
        );

        assertFalse(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.payload();
        assertEquals(
                "Delete is only allowed for files whose current execution contract is delete. Current contract: patch-existing.",
                payload.get("message")
        );
        assertTrue(Files.exists(file));
    }

    @Test
    void deleteToolAllowsDeleteContract() throws Exception {
        Path file = tempDir.resolve("obsolete.js");
        Files.writeString(file, "console.log('obsolete');\n");
        ImplementationToolContext context = newContext(file, ChangeAction.DELETE);

        ToolInvocationResult result = new FileDeleteTool().invoke(
                new LlmToolCall(
                        "call-delete-owned",
                        "Delete",
                        Map.of("file_path", file.toString())
                ),
                context
        );

        assertTrue(result.success());
        assertFalse(Files.exists(file));
        assertEquals(1, context.mutationRecords().size());
    }

    private ImplementationToolContext newContext(Path file, ChangeAction action) {
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
                        new ExecutionFileContractMaterializer().materialize(
                                tempDir,
                                List.of(new FileChange(file.getFileName().toString(), action, "file contract"))
                        ),
                        Set.of("Delete"),
                        5_000L,
                        5_000L,
                        DeliveryMode.PATCH,
                        false,
                        true,
                        false
                ),
                new ImplementationToolPermissionPolicy(
                        new ImplementationToolPermissionProperties(List.of("Delete")),
                        new ImplementationExecutionPolicy()
                ),
                DeliveryMode.PATCH
        );
    }
}
