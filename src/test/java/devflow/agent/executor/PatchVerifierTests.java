package devflow.agent.executor;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchVerifierTests {

    @TempDir
    Path tempDir;

    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final PatchVerifier patchVerifier = new PatchVerifier(
            treeSitterSupport,
            new GeneratedContentGate(new FileProjectWorkspace(), treeSitterSupport)
    );

    @Test
    void verifyInlineScriptReturnsTypedSuccessForParsableScript() {
        ToolResult result = patchVerifier.verifyInlineScript(
                Path.of("index.html"),
                """
                function tick() {
                  return 1;
                }
                """
        );

        assertTrue(result.succeeded());
        assertEquals(ToolName.CONTENT_VERIFY, result.toolName());
    }

    @Test
    void verifyInlineScriptRejectsDuplicatedWrapperAsStructuralFailure() {
        ToolResult result = patchVerifier.verifyInlineScript(
                Path.of("index.html"),
                """
                function tick() {
                  function tick() {
                    return 1;
                  }
                }
                """
        );

        assertEquals(ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID, result.failureCode());
        assertEquals(ToolName.CONTENT_VERIFY, result.toolName());
    }

    @Test
    void verifyInlineStyleReturnsTypedSuccessForParsableStyle() {
        ToolResult result = patchVerifier.verifyInlineStyle(
                Path.of("index.html"),
                """
                #app {
                  color: red;
                }
                """
        );

        assertTrue(result.succeeded());
        assertEquals(ToolName.TREE_SITTER_VERIFY, result.toolName());
    }

    @Test
    void verifyCodeFileReturnsTypedFailureForInvalidContent() {
        ToolResult result = patchVerifier.verifyCodeFile(tempDir, Path.of("game.js"), "const broken =");

        assertEquals(ToolFailureCode.SYNTAX_INVALID, result.failureCode());
        assertEquals(ToolName.CONTENT_VERIFY, result.toolName());
    }

    @Test
    void verifyCodeFileReturnsTypedFailureWhenPreciseAnchorsDisappear() {
        ToolResult result = patchVerifier.verifyCodeFile(tempDir, Path.of("style.css"), "/* only comment */");

        assertEquals(ToolFailureCode.TARGET_NOT_ADDRESSABLE, result.failureCode());
        assertEquals(ToolName.CONTENT_VERIFY, result.toolName());
    }

    @Test
    void verifyInlineStyleReturnsTypedFailureForInvalidStyle() {
        ToolResult result = patchVerifier.verifyInlineStyle(
                Path.of("index.html"),
                """
                #app {
                  color:
                """
        );

        assertEquals(ToolFailureCode.SYNTAX_INVALID, result.failureCode());
        assertEquals(ToolName.TREE_SITTER_VERIFY, result.toolName());
    }
}
