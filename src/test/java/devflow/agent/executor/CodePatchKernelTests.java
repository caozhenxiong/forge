package devflow.agent.executor;

import devflow.agent.editing.FileStateLedger;
import devflow.agent.editing.StructuredDiffHunk;
import devflow.agent.editing.StructuredDiffPatch;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodePatchKernelTests {

    @TempDir
    Path tempDir;

    private final FileStateLedger fileStateLedger = new FileStateLedger();
    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final CodePatchKernel patchKernel = new CodePatchKernel(
            new PatchVerifier(treeSitterSupport, new GeneratedContentGate(new FileProjectWorkspace(), treeSitterSupport))
    );

    @Test
    void appliesInlineStylePatchAndKeepsStyleParsable() {
        String source = """
                #app {
                  color: red;
                }
                """;
        PatchApplyResult result = patchKernel.applyInlineStyle(
                Path.of("index.html"),
                source,
                patch(Path.of("index.html.inline-style.css"), source, 4, List.of(), List.of(
                        "",
                        "body {",
                        "  margin: 0;",
                        "}"
                ))
        );

        assertTrue(result.succeeded());
        assertTrue(result.content().contains("margin: 0;"));
        assertTrue(result.content().contains("#app"));
    }

    @Test
    void appliesPythonCodePatchAndKeepsFileParsable() {
        String source = """
                def tick():
                    return 0
                """;
        PatchApplyResult result = patchKernel.applyCodeFile(
                tempDir,
                Path.of("game.py"),
                source,
                patch(Path.of("game.py"), source, 2, List.of("    return 0"), List.of("    return 1"))
        );

        assertTrue(result.succeeded());
        assertTrue(result.content().contains("return 1"));
    }

    @Test
    void appliesJavaCodePatchAndKeepsFileParsable() {
        String source = """
                class Game {
                  int tick() {
                    return 0;
                  }
                }
                """;
        PatchApplyResult result = patchKernel.applyCodeFile(
                tempDir,
                Path.of("Game.java"),
                source,
                patch(Path.of("Game.java"), source, 3, List.of("    return 0;"), List.of("    return 1;"))
        );

        assertTrue(result.succeeded());
        assertTrue(result.content().contains("return 1;"));
    }

    @Test
    void appliesCssCodePatchAndKeepsFileParsable() {
        String source = """
                #app {
                  color: red;
                }
                """;
        PatchApplyResult result = patchKernel.applyCodeFile(
                tempDir,
                Path.of("style.css"),
                source,
                patch(Path.of("style.css"), source, 1, List.of(
                        "#app {",
                        "  color: red;",
                        "}"
                ), List.of(
                        "#app {",
                        "  color: blue;",
                        "}"
                ))
        );

        assertTrue(result.succeeded());
        assertTrue(result.content().contains("color: blue;"));
    }

    @Test
    void anchorMissingReturnsTypedPatchAnchorFailure() {
        PatchApplyResult result = patchKernel.applyCodeFile(
                tempDir,
                Path.of("game.py"),
                "",
                patch(Path.of("game.py"), "def tick():\n    return 0\n", 2, List.of("    return 0"), List.of("    return 1"))
        );

        assertEquals(ToolFailureCode.PATCH_ANCHOR_MISSING, result.failureResult().failureCode());
        assertEquals(ToolName.PATCH_APPLY, result.failureResult().toolName());
    }

    private StructuredDiffPatch patch(
            Path relativePath,
            String source,
            int startLine,
            List<String> beforeLines,
            List<String> afterLines
    ) {
        return new StructuredDiffPatch(
                fileStateLedger.capture(relativePath, source).contentHash(),
                List.of(new StructuredDiffHunk(startLine, beforeLines, afterLines))
        );
    }
}
