package devflow.agent.executor;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedContentGateTests {

    @TempDir
    Path tempDir;

    @Test
    void emptyOutputIsReportedAsLocalRetryableFailure() {
        GeneratedContentGate gate = new GeneratedContentGate(new FileProjectWorkspace(), new TreeSitterSupport());

        GateReport report = gate.evaluate(new GeneratedContentGateInput(tempDir, Path.of("game.js"), ""));

        assertFalse(report.passed());
        assertEquals("EMPTY_OUTPUT", report.issues().get(0).code());
        assertEquals(GateFailureDisposition.LOCAL_RETRYABLE, report.issues().get(0).disposition());
        assertEquals(GenerationFailureType.RESULT_FILE_INVALID, gate.failureTypeFor(report));
        assertEquals(ToolFailureCode.GENERATED_CONTENT_EMPTY, gate.toolFailureCodeFor(report));
    }

    @Test
    void malformedHtmlIsRejectedBeforeWrite() {
        GeneratedContentGate gate = new GeneratedContentGate(new FileProjectWorkspace(), new TreeSitterSupport());

        GateReport report = gate.evaluate(new GeneratedContentGateInput(
                tempDir,
                Path.of("index.html"),
                "<html><body><script>const a = 1;</body></html>"
        ));

        assertFalse(report.passed());
        assertTrue(gate.renderFailure(report).contains("未闭合"));
        assertEquals(GateFailureDisposition.LOCAL_RETRYABLE, report.issues().get(0).disposition());
        assertEquals(ToolFailureCode.HTML_STRUCTURE_INVALID, gate.toolFailureCodeFor(report));
    }

    @Test
    void invalidJavaScriptIsClassifiedAsParseFailure() {
        GeneratedContentGate gate = new GeneratedContentGate(new FileProjectWorkspace(), new TreeSitterSupport());

        GateReport report = gate.evaluate(new GeneratedContentGateInput(
                tempDir,
                Path.of("game.js"),
                "function tick() {"
        ));

        assertFalse(report.passed());
        assertEquals("TREE_SITTER_PARSE_FAILED", report.issues().get(0).code());
        assertEquals(GenerationFailureType.TREE_SITTER_PARSE_FAILED, gate.failureTypeFor(report));
        assertEquals(ToolFailureCode.TREE_SITTER_PARSE_FAILED, gate.toolFailureCodeFor(report));
    }

    @Test
    void preciseCodeFileWithoutStableAnchorsIsRejected() {
        GeneratedContentGate gate = new GeneratedContentGate(new FileProjectWorkspace(), new TreeSitterSupport());

        GateReport report = gate.evaluate(new GeneratedContentGateInput(
                tempDir,
                Path.of("style.css"),
                "/* only comment */"
        ));

        assertFalse(report.passed());
        assertEquals("PRECISE_EDIT_ANCHORS_MISSING", report.issues().getFirst().code());
        assertEquals(ToolFailureCode.PATCH_ANCHOR_MISSING, gate.toolFailureCodeFor(report));
    }

    @Test
    void duplicatedFunctionWrapperIsRejectedAsStructuralFailure() {
        GeneratedContentGate gate = new GeneratedContentGate(new FileProjectWorkspace(), new TreeSitterSupport());

        GateReport report = gate.evaluate(new GeneratedContentGateInput(
                tempDir,
                Path.of("game.js"),
                """
                        function renderGrid() {
                          function renderGrid() {
                            return true;
                          }
                        }
                        """
        ));

        assertFalse(report.passed());
        assertEquals("JAVASCRIPT_STRUCTURE_INVALID", report.issues().getFirst().code());
        assertEquals(ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID, gate.toolFailureCodeFor(report));
    }

    @Test
    void externalizedRuntimeHostRejectsLingeringInlineAppScript() {
        GeneratedContentGate gate = new GeneratedContentGate(new FileProjectWorkspace(), new TreeSitterSupport());

        GateReport report = gate.evaluate(new GeneratedContentGateInput(
                tempDir,
                Path.of("index.html"),
                """
                        <!DOCTYPE html>
                        <html>
                        <head><title>demo</title></head>
                        <body>
                          <main id="app-root"></main>
                          <script id="app-script">
                            console.log('still inline');
                          </script>
                          <script src="./index.app.js"></script>
                        </body>
                        </html>
                        """,
                HtmlRuntimeOwnershipContract.externalCompanion(Path.of("index.html"), List.of(Path.of("index.app.js"))),
                List.of(Path.of("index.app.js"))
        ));

        assertFalse(report.passed());
        assertEquals("RUNTIME_WIRING_INVALID", report.issues().getFirst().code());
        assertTrue(gate.renderFailure(report).contains("app-script"));
        assertEquals(ToolFailureCode.RUNTIME_WIRING_INVALID, gate.toolFailureCodeFor(report));
    }

    @Test
    void headLocalRuntimeScriptMustUseDeferredLoading() {
        GeneratedContentGate gate = new GeneratedContentGate(new FileProjectWorkspace(), new TreeSitterSupport());

        GateReport report = gate.evaluate(new GeneratedContentGateInput(
                tempDir,
                Path.of("index.html"),
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <script src="./index.app.js"></script>
                        </head>
                        <body>
                          <button id="start-btn">开始</button>
                        </body>
                        </html>
                        """,
                HtmlRuntimeOwnershipContract.externalCompanion(Path.of("index.html"), List.of(Path.of("index.app.js"))),
                List.of(Path.of("index.app.js"))
        ));

        assertFalse(report.passed());
        assertEquals("RUNTIME_WIRING_INVALID", report.issues().getFirst().code());
        assertTrue(gate.renderFailure(report).contains("defer/async"));
        assertEquals(ToolFailureCode.RUNTIME_WIRING_INVALID, gate.toolFailureCodeFor(report));
    }
}
