package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ValidationMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationCompletenessGateTests {

    @TempDir
    Path tempDir;

    @Test
    void passesWhenInspectionPasses() throws Exception {
        Files.writeString(
                tempDir.resolve("game-engine.js"),
                """
                        class GameEngine {
                          movePiece(dx, dy) {
                            this.x = (this.x || 0) + dx;
                            this.y = (this.y || 0) + dy;
                            return true;
                          }
                        }
                        """
        );

        ImplementationCompletenessGate gate = new ImplementationCompletenessGate(new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        ));
        ImplementationCompletenessGateOutcome outcome = gate.evaluate(new ImplementationCompletenessGateInput(
                tempDir,
                new Subtask(
                        "实现方块移动",
                        "补齐当前方块的移动行为",
                        List.of("CAP-2"),
                        List.of("方块移动逻辑"),
                        List.of(),
                        List.of("方块可左右移动"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game-engine.js", ChangeAction.WRITE, "补齐移动逻辑"))
                ),
                QualityPlan.empty(),
                false
        ));

        assertTrue(outcome.inspection().passed());
        assertTrue(outcome.report().passed());
    }

    @Test
    void deferredCapabilityFindingsDoNotBlockIntermediateSubtask() throws Exception {
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

        ImplementationCompletenessGate gate = new ImplementationCompletenessGate(new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        ));
        ImplementationCompletenessGateOutcome outcome = gate.evaluate(new ImplementationCompletenessGateInput(
                tempDir,
                new Subtask(
                        "实现方块移动",
                        "补齐当前方块的移动行为",
                        List.of("CAP-2"),
                        List.of("方块移动逻辑"),
                        List.of("游戏循环逻辑"),
                        List.of("方块可左右移动"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game-engine.js", ChangeAction.WRITE, "补齐移动逻辑"))
                ),
                QualityPlan.empty(),
                false
        ));

        assertTrue(outcome.inspection().passed(), outcome.inspection().evidenceMarkdown());
        assertTrue(outcome.report().passed());
    }

    @Test
    void incompleteOwnedCapabilityBlocksWithLocalRetryableDisposition() throws Exception {
        Files.writeString(
                tempDir.resolve("game-engine.js"),
                """
                        class GameEngine {
                          movePiece(dx, dy) {
                          }
                        }
                        """
        );

        ImplementationCompletenessGate gate = new ImplementationCompletenessGate(new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        ));
        ImplementationCompletenessGateOutcome outcome = gate.evaluate(new ImplementationCompletenessGateInput(
                tempDir,
                new Subtask(
                        "实现方块移动",
                        "补齐当前方块的移动行为",
                        List.of("CAP-2"),
                        List.of("movePiece", "方块移动逻辑"),
                        List.of(),
                        List.of("方块可左右移动"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game-engine.js", ChangeAction.WRITE, "补齐移动逻辑"))
                ),
                QualityPlan.empty(),
                false
        ));

        assertFalse(outcome.inspection().passed());
        assertFalse(outcome.report().passed());
        assertEquals(GateFailureDisposition.LOCAL_RETRYABLE, outcome.report().issues().get(0).disposition());
        assertTrue(gate.toBlockingReviewResult(outcome, DocumentLanguage.ZH).summary().contains("骨架"));
    }

    @Test
    void finalSubtaskBlocksEvenIfCapabilityWasPreviouslyDeferred() throws Exception {
        Files.writeString(
                tempDir.resolve("game-engine.js"),
                """
                        class GameEngine {
                          update() {
                          }
                        }
                        """
        );

        ImplementationCompletenessGate gate = new ImplementationCompletenessGate(new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        ));
        ImplementationCompletenessGateOutcome outcome = gate.evaluate(new ImplementationCompletenessGateInput(
                tempDir,
                new Subtask(
                        "补齐最终行为",
                        "完成最终行为闭环",
                        List.of("CAP-3"),
                        List.of("游戏循环逻辑"),
                        List.of("后续 polish"),
                        List.of("游戏循环可运行"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game-engine.js", ChangeAction.WRITE, "补齐循环逻辑"))
                ),
                QualityPlan.empty(),
                true
        ));

        assertFalse(outcome.report().passed());
    }

    @Test
    void structureRiskNoLongerBlocksSubtaskBeforeContractOrRunnableChecks() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <canvas id="board"></canvas>
                          <script>
                          function startGame() {
                            return true;
                          }
                          </script>
                        </body>
                        </html>
                        """
        );

        ImplementationCompletenessGate gate = new ImplementationCompletenessGate(new ImplementationCompletenessCheck(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        ));
        QualityPlan qualityPlan = new QualityPlanFactory().build(
                new devflow.agent.validation.ProjectFingerprint(
                        "static-web",
                        "none",
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        "index.html",
                        Set.of("index.html"),
                        List.of()
                ),
                new ContractView(
                        null,
                        null,
                        new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "runtime-surface-renders")).normalized(),
                        ConstraintSourceMetadata.empty()
                ),
                ValidationMetadata.empty(),
                new RuntimeSnapshot(
                        "index.html",
                        "demo",
                        8,
                        1,
                        List.of("body", "#board", "canvas"),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                List.of()
        );

        ImplementationCompletenessGateOutcome outcome = gate.evaluate(new ImplementationCompletenessGateInput(
                tempDir,
                new Subtask(
                        "补齐交互型宿主页面",
                        "继续在宿主 HTML 中堆叠主要逻辑",
                        List.of("CAP-1"),
                        List.of("宿主 HTML 内联主逻辑"),
                        List.of(),
                        List.of("页面可打开"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "继续补 inline script"))
                ),
                qualityPlan,
                true
        ));

        assertTrue(outcome.report().passed());
    }
}
