package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ExecutionContract;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectIntegrationCheckTests {

    @TempDir
    Path tempDir;

    @Test
    void htmlEntryFailsWhenSiblingScriptExistsButEntryHasNoRuntimeWiring() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <meta charset="UTF-8">
                          <title>Tetris</title>
                        </head>
                        <body>
                          <main id="app-root">
                            <canvas id="game-canvas"></canvas>
                          </main>
                        </body>
                        </html>
                        """
        );
        Files.writeString(
                tempDir.resolve("game.js"),
                """
                        const canvas = document.getElementById('game-canvas');
                        console.log(canvas);
                        """
        );

        ArchitectIntegrationCheck check = new ArchitectIntegrationCheck(new FileProjectWorkspace(), new TreeSitterSupport());
        ArchitectIntegrationCheckResult result = check.verify(
                tempDir,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "surface-renders"))
        );

        assertFalse(result.passed());
        assertEquals(ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID, result.failureReason());
        assertEquals(devflow.agent.review.ImplementationPatchTarget.PATCH_RUNTIME_WIRING, result.implementationPatchTarget());
        assertNotNull(result.runtimeContract());
        assertTrue(result.runtimeContract().externalCompanion());
        assertEquals(List.of(Path.of("game.js")), result.runtimeContract().runtimePaths());
        assertTrue(result.details().contains("运行时脚本接线") || result.details().contains("脚本资源"));
    }

    @Test
    void htmlEntryDoesNotFailArchitectCheckForSelectorNamesThatOnlyExistAtRuntime() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <meta charset="UTF-8">
                          <title>Tetris</title>
                          <script src="./game.js"></script>
                        </head>
                        <body>
                          <main id="app-root">
                            <button id="start-btn">开始</button>
                          </main>
                        </body>
                        </html>
                        """
        );
        Files.writeString(
                tempDir.resolve("game.js"),
                """
                        const button = document.getElementById('startBtn');
                        console.log(button);
                        """
        );

        ArchitectIntegrationCheck check = new ArchitectIntegrationCheck(new FileProjectWorkspace(), new TreeSitterSupport());
        ArchitectIntegrationCheckResult result = check.verify(
                tempDir,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "surface-renders"))
        );

        assertTrue(result.passed());
    }

    @Test
    void runnableMilestoneAllowsHtmlSkeletonButFinalCheckStillRequiresBehaviorCompletion() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <meta charset="UTF-8">
                          <title>Tetris</title>
                        </head>
                        <body>
                          <main id="app-root">
                            <canvas id="game-canvas"></canvas>
                            <button id="start-btn">开始</button>
                          </main>
                          <script>
                            function update() {
                            }

                            function render() {
                            }

                            document.addEventListener('DOMContentLoaded', () => {
                              const button = document.getElementById('start-btn');
                              if (button) {
                                button.textContent = '开始';
                              }
                            });
                          </script>
                        </body>
                        </html>
                        """
        );

        ArchitectIntegrationCheck check = new ArchitectIntegrationCheck(new FileProjectWorkspace(), new TreeSitterSupport());
        ExecutionContract contract = new ExecutionContract(
                true,
                "html-entry",
                true,
                true,
                List.of("page-opens", "runtime-surface-renders", "game-starts")
        );

        ArchitectIntegrationCheckResult runnableMilestoneResult = check.verifyRunnableMilestone(tempDir, contract);
        ArchitectIntegrationCheckResult finalResult = check.verify(tempDir, contract);

        assertTrue(runnableMilestoneResult.passed());
        assertFalse(finalResult.passed());
        assertEquals(ArchitectIntegrationFailureReason.IMPLEMENTATION_INCOMPLETE, finalResult.failureReason());
    }
}
