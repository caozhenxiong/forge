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
}
