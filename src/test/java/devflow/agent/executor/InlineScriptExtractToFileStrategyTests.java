package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineScriptExtractToFileStrategyTests {

    @Test
    void externalizeRemovesInlineAppScriptAndAppendsCompanionRuntimeScript() {
        InlineScriptExtractToFileStrategy strategy = new InlineScriptExtractToFileStrategy(
                new HtmlPreciseEditor(new TreeSitterSupport())
        );

        GeneratedFileOutput output = strategy.externalize(
                Path.of("index.html"),
                """
                        <!DOCTYPE html>
                        <html>
                        <head><title>demo</title></head>
                        <body>
                          <main id="app-root"></main>
                          <script id="app-script">
                            console.log('inline runtime');
                          </script>
                        </body>
                        </html>
                        """,
                "console.log('external runtime');"
        );

        assertFalse(output.primaryContent().contains("id=\"app-script\""));
        assertTrue(output.primaryContent().contains("<script src=\"./index.app.js\"></script>"));
        assertEquals(1, output.auxiliaryWrites().size());
        assertEquals(Path.of("index.app.js"), output.auxiliaryWrites().getFirst().relativePath());
    }
}
