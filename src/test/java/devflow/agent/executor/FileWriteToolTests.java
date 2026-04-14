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
        ImplementationToolContext context = newContext(file, DeliveryMode.REWORK);

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
        ImplementationToolContext context = newContext(file, DeliveryMode.PATCH);

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
        assertEquals("Write is only allowed for new files unless the current delivery mode is REWORK. Use Read + Edit for existing files in PATCH.", payload.get("message"));
        assertEquals("export const ready = true;\n", Files.readString(file));
    }

    private ImplementationToolContext newContext(Path file, DeliveryMode deliveryMode) throws Exception {
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
                        Set.of(Path.of("app.js")),
                        Set.of("Read", "Write"),
                        5_000L,
                        5_000L
                ),
                new ImplementationToolPermissionPolicy(
                        new ImplementationToolPermissionProperties(List.of("Read", "Write")),
                        new ImplementationExecutionPolicy()
                ),
                deliveryMode,
                List.of(new FileChange("app.js", ChangeAction.WRITE, "更新 app.js"))
        );
        context.readFileStateLedger().put(
                file,
                new CoderReadFileState(Files.readString(file), Files.getLastModifiedTime(file).toMillis(), null, null, false)
        );
        return context;
    }
}
