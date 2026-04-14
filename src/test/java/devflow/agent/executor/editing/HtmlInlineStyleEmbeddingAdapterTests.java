package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.precise.HtmlPreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HtmlInlineStyleEmbeddingAdapterTests {

    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final EditUnitPlanner editUnitPlanner = new EditUnitPlanner(new TreeSitterTargetLocator(treeSitterSupport));
    private final PatchUnitSizer patchUnitSizer = new PatchUnitSizer(new PatchBudgetPolicy(), new PatchFailureRouter());
    private final HtmlInlineStyleEmbeddingAdapter adapter =
            new HtmlInlineStyleEmbeddingAdapter(treeSitterSupport, editUnitPlanner, patchUnitSizer, new HtmlPreciseEditor(treeSitterSupport));

    @Test
    void buildsEditPlanForStableAppStyle() {
        String html = """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    #app {
                      color: red;
                    }
                  </style>
                </head>
                <body>
                  <main id="app-root"></main>
                </body>
                </html>
                """;

        InlineStyleEditPlan editPlan = adapter.buildEditPlan(Path.of("index.html"), html);

        assertNotNull(editPlan);
        assertNotNull(editPlan.workingSet());
        assertNotNull(editPlan.patchPlan());
        assertFalse(editPlan.patchPlan().units().isEmpty());
    }

    @Test
    void reportsUnsupportedWhenAppStyleIsMissing() {
        String html = """
                <!doctype html>
                <html>
                <head>
                  <style>
                    body {
                      color: red;
                    }
                  </style>
                </head>
                <body>
                  <main id="app-root"></main>
                </body>
                </html>
                """;

        assertFalse(adapter.supports(Path.of("index.html"), html));
    }

    @Test
    void mergesUpdatedStyleBackIntoHtml() {
        String html = """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    #app {
                      color: red;
                    }
                  </style>
                </head>
                <body>
                  <main id="app-root"></main>
                </body>
                </html>
                """;

        String merged = adapter.mergeIntoHost(html, "#app {\n  color: blue;\n}");

        assertTrue(merged.contains("color: blue"));
        assertTrue(merged.contains("app-style"));
    }

    @Test
    void multiRuleAppStyleStillBuildsRuleLevelPatchPlan() {
        String html = """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    #app {
                      color: red;
                    }
                    #panel {
                      display: grid;
                    }
                  </style>
                </head>
                <body>
                  <main id="app-root"></main>
                </body>
                </html>
                """;

        assertTrue(adapter.supports(Path.of("index.html"), html));
        InlineStyleEditPlan editPlan = adapter.buildEditPlan(Path.of("index.html"), html);
        assertNotNull(editPlan);
        assertTrue(editPlan.isUsable());
        assertFalse(editPlan.patchPlan().units().isEmpty());
    }
}
