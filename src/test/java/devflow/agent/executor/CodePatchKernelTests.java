package devflow.agent.executor;

import devflow.agent.editing.CodePreciseAction;
import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.CodePreciseOperation;
import devflow.agent.editing.CodePrecisePatch;
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

    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final CodePatchKernel patchKernel = new CodePatchKernel(
            new CodePreciseEditor(treeSitterSupport),
            new PatchVerifier(treeSitterSupport, new GeneratedContentGate(new FileProjectWorkspace(), treeSitterSupport))
    );

    @Test
    void appliesInlineStylePatchAndKeepsStyleParsable() {
        PatchApplyResult result = patchKernel.applyInlineStyle(
                Path.of("index.html"),
                """
                #app {
                  color: red;
                }
                """,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.APPEND_FILE,
                                null,
                                null,
                                null,
                                List.of(
                                        "",
                                        "body {",
                                        "  margin: 0;",
                                        "}"
                                )
                        )
                ))
        );

        assertTrue(result.succeeded());
        assertTrue(result.content().contains("margin: 0;"));
        assertTrue(result.content().contains("#app"));
    }

    @Test
    void appliesPythonCodePatchAndKeepsFileParsable() {
        PatchApplyResult result = patchKernel.applyCodeFile(
                tempDir,
                Path.of("game.py"),
                """
                def tick():
                    return 0
                """,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL_BODY,
                                "tick",
                                "function",
                                null,
                                List.of("return 1")
                        )
                ))
        );

        assertTrue(result.succeeded());
        assertTrue(result.content().contains("return 1"));
    }

    @Test
    void appliesJavaCodePatchAndKeepsFileParsable() {
        PatchApplyResult result = patchKernel.applyCodeFile(
                tempDir,
                Path.of("Game.java"),
                """
                class Game {
                  int tick() {
                    return 0;
                  }
                }
                """,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL_BODY,
                                "tick",
                                "method",
                                null,
                                List.of("return 1;")
                        )
                ))
        );

        assertTrue(result.succeeded());
        assertTrue(result.content().contains("return 1;"));
    }

    @Test
    void appliesCssCodePatchAndKeepsFileParsable() {
        PatchApplyResult result = patchKernel.applyCodeFile(
                tempDir,
                Path.of("style.css"),
                """
                #app {
                  color: red;
                }
                """,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL,
                                "#app",
                                "rule",
                                null,
                                List.of(
                                        "#app {",
                                        "  color: blue;",
                                        "}"
                                )
                        )
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
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL_BODY,
                                "tick",
                                "function",
                                null,
                                List.of("return 1")
                        )
                ))
        );

        assertEquals(ToolFailureCode.PATCH_ANCHOR_MISSING, result.failureResult().failureCode());
        assertEquals(ToolName.PATCH_APPLY, result.failureResult().toolName());
    }
}
