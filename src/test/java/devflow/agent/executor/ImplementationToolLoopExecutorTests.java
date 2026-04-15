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

import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.llm.ChatCapableLlmProvider;
import devflow.agent.executor.llm.LlmChatRequest;
import devflow.agent.executor.llm.LlmChatResponse;
import devflow.agent.executor.llm.LlmChatRole;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.LlmToolCall;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ImplementationToolPermissionProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageType;
import devflow.agent.editing.precise.FileStateLedger;
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

import devflow.agent.executor.implementation.toolloop.ImplementationToolLoopExecutor;
import devflow.agent.executor.implementation.toolloop.ImplementationToolLoopResult;
import devflow.agent.executor.implementation.toolloop.ToolLoopDiagnosticStatus;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.TaskPackage;
class ImplementationToolLoopExecutorTests {

    @TempDir
    Path tempDir;

    private ImplementationToolLoopExecutor newToolLoopExecutor(LlmProvider provider, int maxToolTurns) {
        return new ImplementationToolLoopExecutor(
                provider,
                new ObjectMapper(),
                maxToolTurns,
                new ImplementationToolPermissionPolicy(
                        new ImplementationToolPermissionProperties(List.of()),
                        new ImplementationExecutionPolicy()
                ),
                TestExecutorServices.directExecutorService()
        );
    }

    @Test
    void toolLoopReadsThenEditsExistingFile() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, """
                function tick() {
                  return 0;
                }
                """);

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
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

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-1", "Delete", Map.of("file_path", file.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse("removed", List.of(), null, "stop")
                ),
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
        ImplementationToolLoopExecutor firstExecutor = newToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-1", "Read", Map.of("file_path", file.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse("continue", List.of(), null, "stop")
                ),
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

        ImplementationToolLoopExecutor secondExecutor = newToolLoopExecutor(
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
        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
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
        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse("index.html 已完成", List.of(), null, "stop")
                ),
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
    void toolLoopFinishesWhenDeclaredWriteIsSatisfiedAtTurnLimit() throws Exception {
        Path file = tempDir.resolve("index.html");

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-1",
                                        "Write",
                                        Map.of(
                                                "file_path", file.toString(),
                                                "content", "<!DOCTYPE html><title>Tetris</title>"
                                        )
                                )),
                                null,
                                ""
                        )
                ),
                1
        );

        ImplementationToolLoopResult result = executor.execute(
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
        );

        assertEquals("已完成当前子任务的声明文件交付：创建首页入口", result.finalResponse());
        assertTrue(Files.exists(file));
        assertEquals("<!DOCTYPE html><title>Tetris</title>", Files.readString(file));
        assertEquals(List.of(Path.of("index.html")), result.touchedPaths());
    }

    @Test
    void toolLoopRejectsAssistantOnlyCompletionWhenDeclaredWriteHasNoMutation() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = false;\n");

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse("app.js 已更新", List.of(), null, "stop")
                ),
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
    void toolLoopAllowsAssistantOnlyCompletionInRepairModeWhenMutationHistoryMatchesCurrentState() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = true;\n");
        String beforeContent = "export const ready = false;\n";
        String afterContent = "export const ready = true;\n";
        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.PATCH, false)
                .applyRevisionDirective(devflow.agent.executor.subtask.SubtaskRevisionDirective.patch(
                        List.of(new FileChange("app.js", ChangeAction.WRITE, "继续修复 app.js"))
                ));
        executionState.toolSessionState().recordMutation(new FileMutationRecord(
                ToolLoopMutationOperation.UPDATE,
                Path.of("app.js"),
                true,
                hash(beforeContent),
                true,
                hash(afterContent),
                List.of(),
                1L
        ));

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse("当前修复范围已满足", List.of(), null, "stop")
                ),
                2
        );

        ImplementationToolLoopResult result = executor.execute(
                tempDir,
                runRecord(tempDir),
                subtask("继续修复 app.js", "app.js"),
                taskPackage("继续修复 app.js", "app.js"),
                null,
                QualityPlan.empty(),
                fingerprint("app.js"),
                "继续当前 repair round，只验证当前范围。",
                "",
                null,
                executionState
        );

        assertEquals("当前修复范围已满足", result.finalResponse());
        assertEquals(List.of(Path.of("app.js")), result.touchedPaths());
        assertEquals(afterContent, Files.readString(file));
    }

    @Test
    void toolLoopRejectsAssistantOnlyCompletionWhenCurrentStateDriftedFromMutationTerminalState() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = maybe;\n");
        String beforeContent = "export const ready = false;\n";
        String afterContent = "export const ready = true;\n";
        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.PATCH, false)
                .applyRevisionDirective(devflow.agent.executor.subtask.SubtaskRevisionDirective.patch(
                        List.of(new FileChange("app.js", ChangeAction.WRITE, "继续修复 app.js"))
                ));
        executionState.toolSessionState().recordMutation(new FileMutationRecord(
                ToolLoopMutationOperation.UPDATE,
                Path.of("app.js"),
                true,
                hash(beforeContent),
                true,
                hash(afterContent),
                List.of(),
                1L
        ));

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse("当前修复范围已满足", List.of(), null, "stop")
                ),
                2
        );

        GenerationFailureException exception = assertThrows(
                GenerationFailureException.class,
                () -> executor.execute(
                        tempDir,
                        runRecord(tempDir),
                        subtask("继续修复 app.js", "app.js"),
                        taskPackage("继续修复 app.js", "app.js"),
                        null,
                        QualityPlan.empty(),
                        fingerprint("app.js"),
                        "继续当前 repair round，只验证当前范围。",
                        "",
                        null,
                        executionState
                )
        );

        assertEquals(GenerationFailureType.NO_MATERIAL_CHANGE, exception.report().failureType());
        assertTrue(exception.report().evidence().contains("closureMode=workspace-state"));
        assertTrue(exception.report().evidence().contains("path=app.js"));
    }

    @Test
    void toolLoopKeepsFreshModeWhenFeedbackIsNonEmptyButExecutionStateIsFresh() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = false;\n");

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
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
                                        "Write",
                                        Map.of(
                                                "file_path", file.toString(),
                                                "content", "export const ready = true;\n"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse("done", List.of(), null, "stop")
                ),
                4
        );

        ImplementationToolLoopResult result = executor.execute(
                tempDir,
                runRecord(tempDir),
                new Subtask(
                        "整文件重写 app.js",
                        "整文件重写 app.js",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("完成当前文件修改"),
                        false,
                        DeliveryMode.REWORK,
                        List.of(new FileChange("app.js", ChangeAction.WRITE, "整文件重写 app.js"))
                ),
                taskPackage("整文件重写 app.js", "app.js", DeliveryMode.REWORK),
                null,
                QualityPlan.empty(),
                fingerprint("app.js"),
                "这是一条上游 prose note，但当前还是 fresh implementation。",
                "",
                null,
                new SubtaskExecutionState(DeliveryMode.REWORK, false)
        );

        assertEquals("done", result.finalResponse());
        assertEquals("export const ready = true;\n", Files.readString(file));
    }

    @Test
    void toolLoopContinuesCurrentSubtaskAfterDeniedBashCommand() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "export const ready = false;\n");

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
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
                                                "old_string", "ready = false",
                                                "new_string", "ready = true"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse("done", List.of(), null, "stop")
                ),
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

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
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

    @Test
    void toolLoopUsesRepairScopedChangesInsteadOfOriginalSubtaskPackage() throws Exception {
        Path appFile = tempDir.resolve("app.js");
        Path otherFile = tempDir.resolve("other.js");
        Files.writeString(appFile, "export const ready = false;\n");
        Files.writeString(otherFile, "export const untouched = true;\n");

        ImplementationToolLoopExecutor executor = newToolLoopExecutor(
                new ScriptedChatProvider(
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall("tool-1", "Read", Map.of("file_path", appFile.toString()))),
                                null,
                                ""
                        ),
                        new LlmChatResponse(
                                "",
                                List.of(new LlmToolCall(
                                        "tool-2",
                                        "Edit",
                                        Map.of(
                                                "file_path", otherFile.toString(),
                                                "old_string", "untouched = true",
                                                "new_string", "untouched = false"
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
                                                "file_path", appFile.toString(),
                                                "old_string", "ready = false",
                                                "new_string", "ready = true"
                                        )
                                )),
                                null,
                                ""
                        ),
                        new LlmChatResponse("done", List.of(), null, "stop")
                ),
                6
        );

        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.REWORK, false)
                .applyRevisionDirective(devflow.agent.executor.subtask.SubtaskRevisionDirective.patch(
                        List.of(new FileChange("app.js", ChangeAction.WRITE, "只修 app.js"))
                ));
        ImplementationToolLoopResult result = executor.execute(
                tempDir,
                runRecord(tempDir),
                new Subtask(
                        "同时修改 app.js 和 other.js",
                        "原始子任务覆盖两个文件",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("完成当前文件修改"),
                        false,
                        DeliveryMode.REWORK,
                        List.of(
                                new FileChange("app.js", ChangeAction.WRITE, "更新 app.js"),
                                new FileChange("other.js", ChangeAction.WRITE, "更新 other.js")
                        )
                ),
                taskPackage("同时修改 app.js 和 other.js", "app.js"),
                null,
                QualityPlan.empty(),
                fingerprint("app.js", "other.js"),
                "只修当前失败文件，不要扩散到兄弟文件。",
                "",
                null,
                executionState
        );

        assertEquals("done", result.finalResponse());
        assertTrue(Files.readString(appFile).contains("ready = true"));
        assertTrue(Files.readString(otherFile).contains("untouched = true"));
        assertEquals(List.of(Path.of("app.js")), result.touchedPaths());
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
        return taskPackage(title, path, DeliveryMode.PATCH);
    }

    private TaskPackage taskPackage(String title, String path, DeliveryMode deliveryMode) {
        return new TaskPackage(
                title,
                title,
                deliveryMode.name(),
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

    private String hash(String content) {
        return new FileStateLedger().capture(Path.of("app.js"), content).contentHash();
    }

    private ProjectFingerprint fingerprint(String... fileNames) {
        List<String> names = fileNames == null ? List.of() : List.of(fileNames);
        String firstName = names.isEmpty() ? "" : names.getFirst();
        return new ProjectFingerprint(
                "web",
                "",
                false,
                false,
                false,
                true,
                names.stream().anyMatch(name -> name != null && name.endsWith(".html")),
                names.stream().anyMatch(name -> name != null && name.endsWith(".js")),
                false,
                firstName.endsWith(".html") ? firstName : "",
                java.util.Set.copyOf(names),
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

    private static final class ScriptedChatProvider extends devflow.agent.testsupport.RequestBackedLlmProvider implements ChatCapableLlmProvider {

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
