package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubtaskExecutionStateTests {

    @Test
    void revisionDirectiveReplacesActiveChangesAndPrunesOldPatchProgress() {
        SubtaskExecutionState state = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        state.recordPatchProgress(new FilePatchProgressState(
                Path.of("js/game-engine.js"),
                FileEditStrategyNames.PRECISE_CODE,
                "export function tick() {}",
                "hash-a",
                List.of(),
                "js/game-engine.js#code-unit-1"
        ));
        state.recordPatchProgress(new FilePatchProgressState(
                Path.of("index.html"),
                FileEditStrategyNames.PRECISE_HTML,
                "<html></html>",
                "hash-b",
                List.of(),
                "index.html#markup"
        ));

        state.applyRevisionDirective(SubtaskRevisionDirective.retry(List.of(new FileChange(
                "index.html",
                ChangeAction.WRITE,
                "改成只修宿主 HTML",
                FileEditScope.HOST_HTML_PATCH,
                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                true
        ))));

        assertEquals(List.of("index.html"), state.effectiveChanges().stream().map(FileChange::path).toList());
        assertNull(state.filePatchProgress(Path.of("js/game-engine.js")));
        assertEquals("<html></html>", state.filePatchProgress(Path.of("index.html")).workingContent());
    }

    @Test
    void generationFailureFreezesSiblingFilesAndKeepsFailedUnitProgress() {
        SubtaskExecutionState state = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        state.recordPatchProgress(new FilePatchProgressState(
                Path.of("index.html"),
                FileEditStrategyNames.PRECISE_HTML,
                "<html></html>",
                "hash-html",
                List.of("markup-unit"),
                "markup-unit"
        ));
        state.recordPatchProgress(new FilePatchProgressState(
                Path.of("src/app.js"),
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
                                GenerationFailureType.RESULT_FILE_INVALID,
                                1,
                                true,
                                "app.js patch failed",
                                "PATCH_EMPTY",
                                "repair in current unit"
                        ),
                        new FilePatchProgressState(
                                Path.of("src/app.js"),
                                FileEditStrategyNames.PRECISE_CODE,
                                "export function tick() {}\n",
                                "hash-js",
                                List.of("code-unit-1", "code-unit-2"),
                                "code-unit-16"
                        )
                )
        );

        assertEquals(List.of("src/app.js"), state.effectiveChanges().stream().map(FileChange::path).toList());
        assertNull(state.filePatchProgress(Path.of("index.html")));
        FilePatchProgressState progressState = state.filePatchProgress(Path.of("src/app.js"));
        assertEquals("code-unit-16", progressState.currentUnitLabel());
        assertEquals(List.of("code-unit-1", "code-unit-2"), progressState.completedUnitLabels());
    }
}
