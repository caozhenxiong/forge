package devflow.agent.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlDocumentInspectorTests {

    @Test
    void unwrapSingleWrappedElementBodyExtractsScriptBody() {
        String extracted = HtmlDocumentInspector.unwrapSingleWrappedElementBody(
                """
                <script id="app-script">
                function startGame() {
                  return 1;
                }
                </script>
                """,
                "script"
        );

        assertEquals("""
                function startGame() {
                  return 1;
                }""", extracted);
    }

    @Test
    void unwrapSingleWrappedElementBodyExtractsMainInnerHtml() {
        String extracted = HtmlDocumentInspector.unwrapSingleWrappedElementBody(
                """
                <main id="app-root">
                  <canvas id="board"></canvas>
                </main>
                """,
                "main"
        );

        assertEquals("<canvas id=\"board\"></canvas>", extracted);
    }

    @Test
    void unwrapSingleWrappedElementBodyRejectsMixedOuterStructure() {
        String extracted = HtmlDocumentInspector.unwrapSingleWrappedElementBody(
                """
                <script>const value = 1;</script>
                <div>extra</div>
                """,
                "script"
        );

        assertNull(extracted);
        assertTrue(HtmlDocumentInspector.containsElementTag("<script>const value = 1;</script>", "script"));
    }
}
