package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;

import devflow.agent.editing.precise.FileStateLedger;
import devflow.agent.editing.precise.ExactReplaceEdit;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
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
                exactEdit(
                        ProjectPathSupport.inlineStyleSyntheticPath(Path.of("index.html")),
                        source,
                        source,
                        source + """

                        body {
                          margin: 0;
                        }
                        """
                )
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
                exactEdit(
                        Path.of("game.py"),
                        source,
                        "    return 0",
                        "    return 1"
                )
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
                exactEdit(
                        Path.of("Game.java"),
                        source,
                        "    return 0;",
                        "    return 1;"
                )
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
                exactEdit(
                        Path.of("style.css"),
                        source,
                        source,
                        """
                        #app {
                          color: blue;
                        }
                        """
                )
        );

        assertTrue(result.succeeded());
        assertTrue(result.content().contains("color: blue;"));
    }

    @Test
    void missingOldTextReturnsTypedExactEditFailure() {
        String currentContent = """
                def start():
                    return 0
                """;
        PatchApplyResult result = patchKernel.applyCodeFile(
                tempDir,
                Path.of("game.py"),
                currentContent,
                exactEdit(
                        Path.of("game.py"),
                        currentContent,
                        "    return 2",
                        "    return 1"
                )
        );

        assertEquals(ToolFailureCode.TARGET_NOT_FOUND, result.failureResult().failureCode());
        assertEquals(ToolName.PATCH_APPLY, result.failureResult().toolName());
    }

    private ExactReplaceEdit exactEdit(
            Path targetPath,
            String source,
            String oldText,
            String newText
    ) {
        return new ExactReplaceEdit(
                targetPath.toString(),
                fileStateLedger.capture(targetPath, source).contentHash(),
                oldText,
                newText,
                false
        );
    }
}
