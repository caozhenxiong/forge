package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationBudgetProfile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationBudgetProfileTests {

    @Test
    void documentFullDraftOutputRatioCanBeOverriddenBySystemProperty() {
        String key = "devflow.generation-budget.document-full-draft-output-ratio";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "0.85");
            assertEquals(0.85d, GenerationBudgetProfile.documentFullDraftOutputRatio());
        } finally {
            restore(key, previous);
        }
    }

    @Test
    void wholeFileRewriteUsesConfiguredOutputRatio() {
        String key = "devflow.generation-budget.patch-budget-ratio";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "0.55");
            assertEquals(0.55d, GenerationBudgetProfile.wholeFileRewriteOutputRatio(DeliveryMode.PATCH));
        } finally {
            restore(key, previous);
        }
    }

    @Test
    void embeddedPatchKindReadsRuntimeBudgetOverrides() {
        String scriptBudgetKey = "devflow.generation-budget.inline-script-unit-output-ratio";
        String stylePreviewKey = "devflow.generation-budget.inline-style-preview-chars";
        String previousScriptBudget = System.getProperty(scriptBudgetKey);
        String previousStylePreview = System.getProperty(stylePreviewKey);
        try {
            System.setProperty(scriptBudgetKey, "0.8");
            System.setProperty(stylePreviewKey, "888");
            assertEquals(0.8d, EmbeddedPatchKind.SCRIPT.defaultOutputBudgetRatio());
            assertEquals(888, EmbeddedPatchKind.STYLE.previewChars());
        } finally {
            restore(scriptBudgetKey, previousScriptBudget);
            restore(stylePreviewKey, previousStylePreview);
        }
    }

    private void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, previous);
    }
}
