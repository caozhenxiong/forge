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

import devflow.agent.executor.generation.GenerationBudgetProfile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationBudgetProfileTests {

    @Test
    void documentFullDraftOutputRatioUsesStableDefaultProfile() {
        assertEquals(
                GenerationBudgetProfile.DEFAULT_FULL_BUDGET_RATIO,
                GenerationBudgetProfile.documentFullDraftOutputRatio()
        );
    }

    @Test
    void wholeFileRewriteUsesPatchBudgetForPatchMode() {
        assertEquals(
                GenerationBudgetProfile.patchOutputRatio(),
                GenerationBudgetProfile.wholeFileRewriteOutputRatio(DeliveryMode.PATCH)
        );
    }

    @Test
    void embeddedPatchKindDelegatesToRuntimeBudgetProfile() {
        assertEquals(
                GenerationBudgetProfile.inlineScriptUnitOutputRatio(),
                EmbeddedPatchKind.SCRIPT.defaultOutputBudgetRatio()
        );
        assertEquals(
                GenerationBudgetProfile.inlineStylePreviewChars(),
                EmbeddedPatchKind.STYLE.previewChars()
        );
    }
}
