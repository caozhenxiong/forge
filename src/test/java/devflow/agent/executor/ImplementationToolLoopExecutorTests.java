package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationToolLoopExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void toolLoopReadsThenEditsExistingFile() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, """
                function tick() {
                  return 0;
                }
                """);

        ImplementationToolLoopExecutor executor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-1", "Read", Map.of("file_path", file.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-2",
                                        "Edit",
                                        Map.of(
                                                "file_path", file.toString(),
                                                "old_string", "return 0;",
                                                "new_string", "return 1;"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse("done", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                6
        );

        ImplementationToolLoopResult result = executor.execute(
                tempDir,
                runRecord(tempDir),
                subtask("更新 tick", "app.js"),
                taskPackage("更新 tick", "app.js"),
                null,
                QualityPlan.empty(),
                fingerprint("app.js"),
                "",
                "",
                null,
                new SubtaskExecutionState(DeliveryMode.PATCH, false)
        );

        assertEquals("done", result.finalResponse());
        assertTrue(Files.readString(file).contains("return 1;"));
        assertEquals(List.of(Path.of("app.js")), result.touchedPaths());
    }

    @Test
    void toolLoopCanDeleteOwnedFile() throws Exception {
        Path file = tempDir.resolve("obsolete.js");
        Files.writeString(file, "console.log('obsolete');");

        ImplementationToolLoopExecutor executor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-1", "Delete", Map.of("file_path", file.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse("removed", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                4
        );

        ImplementationToolLoopResult result = executor.execute(
                tempDir,
                runRecord(tempDir),
                new Subtask(
                        "删除旧文件",
                        "清理旧 runtime 资产",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("旧文件已删除"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("obsolete.js", ChangeAction.DELETE, "删除废弃文件"))
                ),
                taskPackage("删除旧文件", "obsolete.js"),
                null,
                QualityPlan.empty(),
                fingerprint("obsolete.js"),
                "",
                "",
                null,
                new SubtaskExecutionState(DeliveryMode.PATCH, false)
        );

        assertEquals("removed", result.finalResponse());
        assertFalse(Files.exists(file));
        assertEquals(List.of(Path.of("obsolete.js")), result.touchedPaths());
    }

    @Test
    void toolLoopCarriesReadStateAcrossRetryAfterFailedAssistantOnlyCompletion() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, """
                function tick() {
                  return 0;
                }
                """);

        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.PATCH, false);
        ImplementationToolLoopExecutor firstExecutor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-1", "Read", Map.of("file_path", file.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse("continue", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                4
        );

        GenerationFailureException firstFailure = assertThrows(
                GenerationFailureException.class,
                () -> firstExecutor.execute(
                        tempDir,
                        runRecord(tempDir),
                        subtask("读取 tick", "app.js"),
                        taskPackage("读取 tick", "app.js"),
                        null,
                        QualityPlan.empty(),
                        fingerprint("app.js"),
                        "",
                        "",
                        null,
                        executionState
                )
        );
        assertEquals(GenerationFailureType.NO_MATERIAL_CHANGE, firstFailure.report().failureType());

        ImplementationToolLoopExecutor secondExecutor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-2",
                                        "Edit",
                                        Map.of(
                                                "file_path", file.toString(),
                                                "old_string", "return 0;",
                                                "new_string", "return 2;"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse("done", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                4
        );

        ImplementationToolLoopResult result = secondExecutor.execute(
                tempDir,
                runRecord(tempDir),
                subtask("继续 tick", "app.js"),
                taskPackage("继续 tick", "app.js"),
                null,
                QualityPlan.empty(),
                fingerprint("app.js"),
                "继续修复当前子任务",
                "",
                null,
                executionState
        );

        assertEquals("done", result.finalResponse());
        assertTrue(Files.readString(file).contains("return 2;"));
        assertEquals(List.of(Path.of("app.js")), result.touchedPaths());
        assertEquals(1, executionState.toolSessionState().diagnostics().size());
        assertEquals(ToolLoopDiagnosticStatus.VALID, executionState.toolSessionState().diagnostics().getFirst().status());
        assertTrue(executionState.toolSessionState().transcript().stream()
                .anyMatch(message -> message.role() == LlmChatRole.ASSISTANT && message.content().contains("continue")));
    }

    @Test
    void toolLoopContinuesCurrentSubtaskAfterTruncatedAssistantResponse() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, """
                function tick() {
                  return 0;
                }
                """);

        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.PATCH, false);
        ImplementationToolLoopExecutor executor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse("继续补完当前修改", List.of(), null, "length"),
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-1", "Read", Map.of("file_path", file.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-2",
                                        "Edit",
                                        Map.of(
                                                "file_path", file.toString(),
                                                "old_string", "return 0;",
                                                "new_string", "return 3;"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse("done", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                6
        );

        ImplementationToolLoopResult result = executor.execute(
                tempDir,
                runRecord(tempDir),
                subtask("继续 tick", "app.js"),
                taskPackage("继续 tick", "app.js"),
                null,
                QualityPlan.empty(),
                fingerprint("app.js"),
                "",
                "",
                null,
                executionState
        );

        assertEquals("done", result.finalResponse());
        assertTrue(Files.readString(file).contains("return 3;"));
        assertTrue(executionState.toolSessionState().transcript().stream()
                .anyMatch(message -> message.role() == LlmChatRole.USER && message.content().contains("长度截断")));
    }

    @Test
    void toolLoopRejectsAssistantOnlyCompletionWhenDeclaredFileWasNeverCreated() {
        ImplementationToolLoopExecutor executor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse("index.html 已完成", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                2
        );

        GenerationFailureException exception = assertThrows(
                GenerationFailureException.class,
                () -> executor.execute(
                        tempDir,
                        runRecord(tempDir),
                        subtask("创建首页入口", "index.html"),
                        taskPackage("创建首页入口", "index.html"),
                        null,
                        QualityPlan.empty(),
                        fingerprint("index.html"),
                        "",
                        "",
                        null,
                        new SubtaskExecutionState(DeliveryMode.PATCH, false)
                )
        );

        assertEquals(GenerationFailureType.NO_MATERIAL_CHANGE, exception.report().failureType());
        assertTrue(exception.report().evidence().contains("terminalMode=assistant-only"));
        assertTrue(exception.report().evidence().contains("WRITE:index.html"));
        assertTrue(exception.report().evidence().contains("exists=false"));
    }

    @Test
    void toolLoopRejectsAssistantOnlyCompletionWhenDeclaredWriteHasNoMutation() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = false;\n");

        ImplementationToolLoopExecutor executor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse("app.js 已更新", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                2
        );

        GenerationFailureException exception = assertThrows(
                GenerationFailureException.class,
                () -> executor.execute(
                        tempDir,
                        runRecord(tempDir),
                        subtask("更新 app.js", "app.js"),
                        taskPackage("更新 app.js", "app.js"),
                        null,
                        QualityPlan.empty(),
                        fingerprint("app.js"),
                        "",
                        "",
                        null,
                        new SubtaskExecutionState(DeliveryMode.PATCH, false)
                )
        );

        assertEquals(GenerationFailureType.NO_MATERIAL_CHANGE, exception.report().failureType());
        assertTrue(exception.report().evidence().contains("path=app.js"));
        assertTrue(exception.report().evidence().contains("exists=true"));
        assertTrue(exception.report().evidence().contains("mutations=[]"));
    }

    @Test
    void toolLoopContinuesCurrentSubtaskAfterDeniedBashCommand() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = false;\n");

        ImplementationToolLoopExecutor executor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-1",
                                        "Bash",
                                        Map.of("command", "cat /etc/passwd > app.js")
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-2", "Read", Map.of("file_path", file.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-3",
                                        "Edit",
                                        Map.of(
                                                "file_path", file.toString(),
                                                "old_string", "export const ready = false;\n",
                                                "new_string", "export const ready = true;\n"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse("done", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                6
        );

        ImplementationToolLoopResult result = executor.execute(
                tempDir,
                runRecord(tempDir),
                subtask("更新 app.js", "app.js"),
                taskPackage("更新 app.js", "app.js"),
                null,
                QualityPlan.empty(),
                fingerprint("app.js"),
                "",
                "",
                null,
                new SubtaskExecutionState(DeliveryMode.PATCH, false)
        );

        assertEquals("done", result.finalResponse());
        assertTrue(Files.readString(file).contains("ready = true"));
    }

    @Test
    void toolLoopRejectsCompletionWhenFileWasChangedAndThenReverted() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = false;\n");

        ImplementationToolLoopExecutor executor = new ImplementationToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-1", "Read", Map.of("file_path", file.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-2",
                                        "Edit",
                                        Map.of(
                                                "file_path", file.toString(),
                                                "old_string", "ready = false",
                                                "new_string", "ready = true"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-3",
                                        "Edit",
                                        Map.of(
                                                "file_path", file.toString(),
                                                "old_string", "ready = true",
                                                "new_string", "ready = false"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse("done", List.of(), null, "stop")
                ),
                new ObjectMapper(),
                6
        );

        GenerationFailureException exception = assertThrows(
                GenerationFailureException.class,
                () -> executor.execute(
                        tempDir,
                        runRecord(tempDir),
                        subtask("更新 app.js", "app.js"),
                        taskPackage("更新 app.js", "app.js"),
                        null,
                        QualityPlan.empty(),
                        fingerprint("app.js"),
                        "",
                        "",
                        null,
                        new SubtaskExecutionState(DeliveryMode.PATCH, false)
                )
        );

        assertEquals(GenerationFailureType.NO_MATERIAL_CHANGE, exception.report().failureType());
        assertTrue(exception.report().evidence().contains("baselineHash="));
        assertTrue(exception.report().evidence().contains("currentHash="));
    }

    private Subtask subtask(String title, String path) {
        return new Subtask(
                title,
                title,
                List.of(),
                List.of(),
                List.of(),
                List.of("完成当前文件修改"),
                false,
                DeliveryMode.PATCH,
                List.of(new FileChange(path, ChangeAction.WRITE, title))
        );
    }

    private TaskPackage taskPackage(String title, String path) {
        return new TaskPackage(
                title,
                title,
                DeliveryMode.PATCH.name(),
                false,
                List.of(path),
                List.of(),
                List.of(),
                List.of(),
                List.of("完成当前文件修改"),
                List.of(),
                List.of(),
                "",
                null
        );
    }

    private ProjectFingerprint fingerprint(String fileName) {
        return new ProjectFingerprint(
                "web",
                "",
                false,
                false,
                false,
                true,
                fileName.endsWith(".html"),
                fileName.endsWith(".js"),
                false,
                fileName.endsWith(".html") ? fileName : "",
                java.util.Set.of(fileName),
                List.of()
        );
    }

    private RunRecord runRecord(Path projectPath) {
        return new RunRecord(
                UUID.randomUUID(),
                projectPath,
                "test",
                "",
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                new EnumMap<>(StageType.class),
                Instant.now(),
                Instant.now()
        );
    }

    private static final class ScriptedChatProvider implements LlmProvider, ChatCapableLlmProvider {

        private final List<LlmChatResponse> responses;
        private int index;

        private ScriptedChatProvider(LlmChatResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
            throw new UnsupportedOperationException("generate is not used in this test provider");
        }

        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            if (index >= responses.size()) {
                return new LlmChatResponse("done", List.of(), null, "stop");
            }
            return responses.get(index++);
        }
    }
}
