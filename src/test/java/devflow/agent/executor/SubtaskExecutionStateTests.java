package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureReport;
import devflow.agent.executor.generation.GenerationFailureType;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        state.applyFileScopedGenerationFailure(
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

        assertEquals(List.of("src/app.js"), state.effectiveChanges().stream().map(FileChange::path).toList());
        assertNull(state.fileEditAttemptState(Path.of("index.html")));
        FileEditAttemptState progressState = state.fileEditAttemptState(Path.of("src/app.js"));
        assertEquals("code-unit-16", progressState.currentTargetLabel());
        assertEquals(List.of("code-unit-1", "code-unit-2"), progressState.completedTargetLabels());
    }
}
