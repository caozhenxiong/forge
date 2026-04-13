package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodePatchFeedbackRendererTests {

    @Test
    void strictSingleSymbolFeedbackMentionsPinnedTargetSymbol() {
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "index.app.js#code-unit-1-a-a", List.of("GameLogic"));

        String patchSchemaFeedback = CodePatchFeedbackRenderer.patchSchemaFeedback(1, Path.of("index.app.js"), unit);
        String scopeFeedback = CodePatchFeedbackRenderer.scopeViolationFeedback(1, Path.of("index.app.js"), unit);

        assertTrue(patchSchemaFeedback.contains("唯一允许的目标符号是 GameLogic"));
        assertTrue(scopeFeedback.contains("唯一允许的目标符号是 GameLogic"));
    }
}
