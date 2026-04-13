package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.subtask.Subtask;
class ImplementationCompletenessCheckTests {

    @TempDir
    Path tempDir;

    @Test
    void inlineScriptNoOpSwitchIsTreatedAsIncompleteBehavior() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="start-btn">开始</button>
                          <script>
                            document.addEventListener('keydown', (event) => {
                              switch (event.key) {
                                case 'ArrowLeft':
                                  break;
                                case 'ArrowRight':
                                  break;
                                default:
                                  break;
                              }
                            });
                          </script>
                        </body>
                        </html>
                        """
        );

        ImplementationCompletenessCheck check = new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        );

        ImplementationCompletenessResult result = check.inspectFiles(
                tempDir,
                List.of(Path.of("index.html"))
        );

        assertFalse(result.passed());
        assertTrue(result.summary().contains("空函数") || result.summary().contains("no-op"));
        assertTrue(result.evidenceMarkdown().contains("switch handler"));
    }

    @Test
    void deferredPlaceholderMethodDoesNotBlockCurrentOwnedCapability() throws Exception {
        Files.writeString(
                tempDir.resolve("game-engine.js"),
                """
                        class GameEngine {
                          movePiece(dx, dy) {
                            this.x = (this.x || 0) + dx;
                            this.y = (this.y || 0) + dy;
                            return true;
                          }

                          update() {
                          }
                        }
                        """
        );

        ImplementationCompletenessCheck check = new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        );

        Subtask subtask = new Subtask(
                "实现方块移动",
                "补齐当前方块的移动行为",
                List.of("CAP-2"),
                List.of("方块移动逻辑"),
                List.of("游戏循环逻辑"),
                List.of("方块可左右移动"),
                false,
                DeliveryMode.INCREMENTAL,
                List.of(new FileChange("game-engine.js", ChangeAction.WRITE, "补齐移动逻辑"))
        );

        ImplementationCompletenessResult result = check.inspectSubtask(tempDir, subtask);

        assertTrue(result.passed(), result.evidenceMarkdown());
    }

    @Test
    void ownedPlaceholderMethodStillBlocksCurrentSubtask() throws Exception {
        Files.writeString(
                tempDir.resolve("game-engine.js"),
                """
                        class GameEngine {
                          movePiece(dx, dy) {
                          }
                        }
                        """
        );

        ImplementationCompletenessCheck check = new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        );

        Subtask subtask = new Subtask(
                "实现方块移动",
                "补齐当前方块的移动行为",
                List.of("CAP-2"),
                List.of("movePiece", "方块移动逻辑"),
                List.of("游戏循环逻辑"),
                List.of("方块可左右移动"),
                false,
                DeliveryMode.INCREMENTAL,
                List.of(new FileChange("game-engine.js", ChangeAction.WRITE, "补齐移动逻辑"))
        );

        ImplementationCompletenessResult result = check.inspectSubtask(tempDir, subtask);

        assertFalse(result.passed());
        assertTrue(result.evidenceMarkdown().contains("movePiece"));
    }

    @Test
    void chinesePlaceholderMarkerStillBlocksCurrentDelivery() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <script>
                            function bootGame() {
                              // 游戏初始化代码将在这里添加
                            }
                          </script>
                        </body>
                        </html>
                        """
        );

        ImplementationCompletenessCheck check = new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        );

        ImplementationCompletenessResult result = check.inspectFiles(
                tempDir,
                List.of(Path.of("index.html"))
        );

        assertFalse(result.passed());
        assertTrue(result.evidenceMarkdown().contains("将在这里添加"));
    }

    @Test
    void loggingOnlyFunctionIsTreatedAsIncompleteBehavior() throws Exception {
        Files.writeString(
                tempDir.resolve("app.js"),
                """
                        function bootGame() {
                          console.log('game boot');
                        }
                        """
        );

        ImplementationCompletenessCheck check = new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        );

        ImplementationCompletenessResult result = check.inspectFiles(
                tempDir,
                List.of(Path.of("app.js"))
        );

        assertFalse(result.passed());
        assertTrue(result.evidenceMarkdown().contains("bootGame"));
    }
}
