package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlInlineScriptEmbeddingAdapterTests {

    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final EditUnitPlanner editUnitPlanner = new EditUnitPlanner(new TreeSitterTargetLocator(treeSitterSupport));
    private final PatchUnitSizer patchUnitSizer = new PatchUnitSizer(new PatchBudgetPolicy(), new PatchFailureRouter());
    private final HtmlInlineScriptEmbeddingAdapter adapter =
            new HtmlInlineScriptEmbeddingAdapter(treeSitterSupport, editUnitPlanner, patchUnitSizer, new HtmlPreciseEditor(treeSitterSupport));

    @Test
    void buildsEditPlanForStableAppScript() {
        String html = """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    body { background: #111; }
                  </style>
                </head>
                <body>
                  <main id="app-root"></main>
                  <script id="app-script">
                  function bindUi() {
                    return 'ui';
                  }

                  function boot() {
                    bindUi();
                    return 'ok';
                  }
                  </script>
                </body>
                </html>
                """;

        InlineScriptEditPlan editPlan = adapter.buildEditPlan(Path.of("index.html"), html);

        assertNotNull(editPlan);
        assertNotNull(editPlan.workingSet());
        assertNotNull(editPlan.patchPlan());
        assertFalse(editPlan.patchPlan().units().isEmpty());
    }

    @Test
    void reportsUnsupportedWhenAppScriptIsMissing() {
        String html = """
                <!doctype html>
                <html>
                <body>
                  <script>
                  console.log('plain');
                  </script>
                </body>
                </html>
                """;

        assertFalse(adapter.supports(Path.of("index.html"), html));
    }

    @Test
    void mergesUpdatedScriptBackIntoHtml() {
        String html = """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    body { background: #111; }
                  </style>
                </head>
                <body>
                  <main id="app-root"></main>
                  <script id="app-script">
                  function boot() {
                    return 'ok';
                  }
                  </script>
                </body>
                </html>
                """;

        String merged = adapter.mergeIntoHost(html, "function boot() {\n  return 'updated';\n}");

        assertTrue(merged.contains("updated"));
        assertTrue(merged.contains("app-script"));
    }
}
