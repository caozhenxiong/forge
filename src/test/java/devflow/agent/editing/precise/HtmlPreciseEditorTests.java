package devflow.agent.editing.precise;

import devflow.agent.parsing.TreeSitterSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlPreciseEditorTests {

    private final HtmlPreciseEditor editor = new HtmlPreciseEditor(new TreeSitterSupport());

    @Test
    void appliesPrecisePatchToAnchoredHtmlSections() {
        String source = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Forge</title>
                  <style id="app-style">
                    body { background: #111; }
                  </style>
                </head>
                <body>
                  <main id="app-root">
                    <p>old</p>
                  </main>
                  <script id="app-script">
                    console.log('old');
                  </script>
                </body>
                </html>
                """;

        String updated = editor.applyPatch(
                source,
                new HtmlPrecisePatch(
                        "<section class=\"board\"></section>",
                        "body { background: #faf7ef; }",
                        "window.appBooted = true;",
                        null,
                        null
                )
        );

        assertTrue(updated.contains("<title>Forge</title>"));
        assertTrue(updated.contains("<section class=\"board\"></section>"));
        assertTrue(updated.contains("body { background: #faf7ef; }"));
        assertTrue(updated.contains("window.appBooted = true;"));
        assertFalse(updated.contains("<p>old</p>"));
        assertFalse(updated.contains("console.log('old');"));
    }

    @Test
    void appendsHeadAndBodyFragmentsWithoutRewritingWholeDocument() {
        String source = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Forge</title>
                  <style id="app-style">
                    body { background: #111; }
                  </style>
                </head>
                <body>
                  <main id="app-root">
                    <p>old</p>
                  </main>
                  <script id="app-script">
                    console.log('old');
                  </script>
                </body>
                </html>
                """;

        String updated = editor.applyPatch(
                source,
                new HtmlPrecisePatch(
                        null,
                        null,
                        null,
                        "<link rel=\"modulepreload\" href=\"game.js\">",
                        "<script src=\"game.js\"></script>"
                )
        );

        assertTrue(updated.contains("<link rel=\"modulepreload\" href=\"game.js\">"));
        assertTrue(updated.contains("<script src=\"game.js\"></script>"));
        assertTrue(updated.contains("<style id=\"app-style\">"));
        assertTrue(updated.contains("<script id=\"app-script\">"));
    }

    @Test
    void treatsAlreadyAppliedAppendFragmentsAsIdempotentNoOp() {
        String source = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Forge</title>
                  <style id="app-style">
                    body { background: #111; }
                  </style>
                  <script src="game.js"></script>
                </head>
                <body>
                  <main id="app-root">
                    <p>old</p>
                  </main>
                </body>
                </html>
                """;

        String updated = editor.applyPatch(
                source,
                new HtmlPrecisePatch(
                        null,
                        null,
                        null,
                        "<script src=\"game.js\"></script>",
                        null
                )
        );

        assertEquals(source, updated);
    }

    @Test
    void reportsUnsupportedWhenAnchorsAreMissing() {
        String source = """
                <!DOCTYPE html>
                <html>
                <body>
                  <h1>No anchors</h1>
                </body>
                </html>
                """;

        assertFalse(editor.supportsPreciseEditing(source));
    }
}
