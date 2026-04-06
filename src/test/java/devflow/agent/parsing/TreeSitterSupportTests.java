package devflow.agent.parsing;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSitterSupportTests {

    private final TreeSitterSupport support = new TreeSitterSupport();

    @Test
    void htmlSnapshotExtractsStaticSelectorsAndCanvas() {
        HtmlStructureSnapshot snapshot = support.inspectHtml("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <body>
                  <button id="startBtn" class="primary">开始</button>
                  <button class="ghost secondary">暂停</button>
                  <canvas id="gameCanvas"></canvas>
                </body>
                </html>
                """);

        assertTrue(snapshot.parseSummary().valid());
        assertTrue(snapshot.hasHtmlRoot());
        assertTrue(snapshot.hasBody());
        assertTrue(snapshot.hasCanvas());
        assertTrue(snapshot.idSelectors().contains("#startBtn"));
        assertTrue(snapshot.buttonSelectors().contains("#startBtn"));
        assertTrue(snapshot.buttonSelectors().contains(".ghost"));
    }

    @Test
    void detectsMalformedHtmlStructure() {
        TreeSitterParseSummary summary = support.analyze(
                Path.of("index.html"),
                """
                <!DOCTYPE html>
                <html>
                <body>
                  <div id="a"><span
                </body>
                </html>
                """
        );

        assertFalse(summary.valid());
    }

    @Test
    void detectsMalformedJavaScriptStructure() {
        TreeSitterParseSummary summary = support.analyze(
                Path.of("app.js"),
                "function broken( { return 1;"
        );

        assertFalse(summary.valid());
    }
}
