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
import devflow.agent.executor.generation.GenerationFailureReport;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.implementation.toolloop.CoderReadFileState;
import devflow.agent.executor.implementation.toolloop.FileMutationRecord;
import devflow.agent.executor.implementation.toolloop.ToolLoopMutationOperation;
import devflow.agent.executor.llm.LlmChatMessage;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.SubtaskRevisionDirective;
class SubtaskExecutionStateTests {

    @Test
    void revisionDirectiveReplacesActiveChangesAndPrunesOldPatchProgress() {
        SubtaskExecutionState state = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        state.recordEditAttemptState(new FileEditAttemptState(
                Path.of("js/game-engine.js"),
                FileEditProtocolNames.TARGETED_REWRITE,
                FileEditStrategyNames.PRECISE_CODE,
                "export function tick() {}",
                "hash-a",
                List.of(),
                "js/game-engine.js#code-unit-1"
        ));
        state.recordEditAttemptState(new FileEditAttemptState(
                Path.of("index.html"),
                FileEditProtocolNames.TARGETED_REWRITE,
                FileEditStrategyNames.PRECISE_HTML,
                "<html></html>",
                "hash-b",
                List.of(),
                "index.html#markup"
        ));

        state = state.applyRevisionDirective(SubtaskRevisionDirective.retry(List.of(new FileChange(
                "index.html",
                ChangeAction.WRITE,
                "改成只修宿主 HTML",
                FileEditScope.HOST_HTML_PATCH,
                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                true
        ))));

        assertEquals(true, state.repairRound());
        assertEquals(List.of("index.html"), state.effectiveChanges().stream().map(FileChange::path).toList());
        assertNull(state.fileEditAttemptState(Path.of("js/game-engine.js")));
        assertEquals("<html></html>", state.fileEditAttemptState(Path.of("index.html")).workingContent());
    }

    @Test
    void generationFailureFreezesSiblingFilesAndKeepsFailedUnitProgress() {
        SubtaskExecutionState state = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        state.recordEditAttemptState(new FileEditAttemptState(
                Path.of("index.html"),
                FileEditProtocolNames.TARGETED_REWRITE,
                FileEditStrategyNames.PRECISE_HTML,
                "<html></html>",
                "hash-html",
                List.of("markup-unit"),
                "markup-unit"
        ));
        state.recordEditAttemptState(new FileEditAttemptState(
                Path.of("src/app.js"),
                FileEditProtocolNames.TARGETED_REWRITE,
                FileEditStrategyNames.PRECISE_CODE,
                "export function tick() {}\n",
                "hash-js",
                List.of("code-unit-1", "code-unit-2"),
                "code-unit-16"
        ));
        Subtask subtask = new Subtask(
                "补齐入口与脚本",
                "入口和脚本一起改",
                List.of(),
                List.of(),
                List.of(),
                List.of("页面可运行"),
                true,
                DeliveryMode.PATCH,
                List.of(
                        new FileChange("index.html", ChangeAction.WRITE, "补宿主", FileEditScope.HOST_HTML_PATCH),
                        new FileChange("src/app.js", ChangeAction.WRITE, "补脚本")
                )
        );

        state = state.applyFileScopedGenerationFailure(
                subtask,
                new GenerationFailureException(
                        new GenerationFailureReport(
                                "src/app.js",
                                DeliveryMode.PATCH.name(),
                                FileEditStrategyNames.PRECISE_CODE,
                                GenerationFailureType.VALIDATION_FAILED,
                                1,
                                true,
                                "app.js patch failed",
                                "NO_MATERIAL_CHANGE",
                                "repair in current unit"
                        ),
                        new FileEditAttemptState(
                                Path.of("src/app.js"),
                                FileEditProtocolNames.TARGETED_REWRITE,
                                FileEditStrategyNames.PRECISE_CODE,
                                "export function tick() {}\n",
                                "hash-js",
                                List.of("code-unit-1", "code-unit-2"),
                                "code-unit-16"
                        )
                )
        );

        assertFalse(state.repairRound());
        assertEquals(List.of("src/app.js"), state.effectiveChanges().stream().map(FileChange::path).toList());
        assertNull(state.fileEditAttemptState(Path.of("index.html")));
        FileEditAttemptState progressState = state.fileEditAttemptState(Path.of("src/app.js"));
        assertEquals("code-unit-16", progressState.currentTargetLabel());
        assertEquals(List.of("code-unit-1", "code-unit-2"), progressState.completedTargetLabels());
    }

    @Test
    void recoveryPolicyStartsFreshRepairRoundAndClearsPriorMutations() {
        SubtaskExecutionState state = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        state.toolSessionState().appendTranscript(LlmChatMessage.assistant("上一轮输出"));
        state.toolSessionState().readFileStateLedger().put(
                Path.of("/tmp/project/src/app.js"),
                new CoderReadFileState("export const ready = false;\n", 1L, null, null, false)
        );
        state.toolSessionState().recordMutation(new FileMutationRecord(
                ToolLoopMutationOperation.UPDATE,
                Path.of("src/app.js"),
                true,
                "hash-before",
                true,
                "hash-after",
                List.of(),
                1L
        ));
        state = state.applyFileScopedGenerationFailure(
                new Subtask(
                        "修脚本",
                        "只修脚本",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("脚本可运行"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("src/app.js", ChangeAction.WRITE, "修脚本"))
                ),
                new GenerationFailureException(
                        new GenerationFailureReport(
                                "src/app.js",
                                DeliveryMode.PATCH.name(),
                                FileEditStrategyNames.PRECISE_CODE,
                                GenerationFailureType.VALIDATION_FAILED,
                                1,
                                true,
                                "patch failed",
                                "NO_MATERIAL_CHANGE",
                                "retry current file"
                        ),
                        new FileEditAttemptState(
                                Path.of("src/app.js"),
                                FileEditProtocolNames.TARGETED_REWRITE,
                                FileEditStrategyNames.PRECISE_CODE,
                                "export const ready = false;\n",
                                "hash-before",
                                List.of(),
                                "code-unit-1"
                        )
                )
        );

        assertFalse(state.repairRound());
        assertEquals(1, state.toolSessionState().mutationRecords().size());
        assertEquals(1, state.toolSessionState().transcript().size());

        state = state.withRecoveryPolicy(new DeliveryPolicy(
                DeliveryPolicyMode.PATCH,
                1,
                1,
                false,
                false,
                true
        ));

        assertTrue(state.repairRound());
        assertTrue(state.toolSessionState().mutationRecords().isEmpty());
        assertTrue(state.toolSessionState().transcript().isEmpty());
        assertEquals(1, state.toolSessionState().readFileStateLedger().snapshotEntries().size());
    }
}
