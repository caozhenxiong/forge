package devflow.agent.executor;

import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditUnitPlannerTests {

    @Test
    void singleInlineScriptEntrySymbolIsSplitIntoAppendAndOrchestratorUnits() {
        EditUnitPlanner planner = new EditUnitPlanner(
                new TreeSitterTargetLocator(new TreeSitterSupport())
        );

        List<EditUnit> units = planner.planInlineScriptUnits(
                Path.of("index.html.inline.js"),
                """
                function bootstrap() {
                }

                document.addEventListener('DOMContentLoaded', bootstrap);
                """
        );

        assertEquals(2, units.size());
        assertTrue(units.get(0).allowedSymbols().isEmpty(), "第一步应先建立 append-only 辅助符号单元");
        assertEquals(List.of("bootstrap"), units.get(1).allowedSymbols(), "第二步应只让入口符号负责编排");
        assertTrue(units.get(0).label().endsWith("-append"));
        assertTrue(units.get(1).label().endsWith("-orchestrator"));
    }

    @Test
    void multiInlineScriptSymbolsStillUseRegularBatching() {
        EditUnitPlanner planner = new EditUnitPlanner(
                new TreeSitterTargetLocator(new TreeSitterSupport())
        );

        List<EditUnit> units = planner.planInlineScriptUnits(
                Path.of("index.html.inline.js"),
                """
                function initGame() {
                }

                function renderBoard() {
                }

                function bindInput() {
                }

                document.addEventListener('DOMContentLoaded', initGame);
                """
        );

        assertEquals(1, units.size());
        assertEquals(List.of("initGame", "renderBoard", "bindInput"), units.get(0).allowedSymbols());
        assertTrue(units.get(0).label().endsWith("-all"));
    }

    @Test
    void codePatchPlanWrapsTargetPathAndStrategy() {
        EditUnitPlanner planner = new EditUnitPlanner(
                new TreeSitterTargetLocator(new TreeSitterSupport())
        );

        PatchPlan patchPlan = planner.planCodePatch(
                Path.of("game.js"),
                """
                export function tick() {
                }
                """
        );

        assertEquals(Path.of("game.js"), patchPlan.targetPath());
        assertEquals(FileEditStrategyNames.PRECISE_CODE, patchPlan.strategyName());
        assertEquals(1, patchPlan.units().size());
    }

    @Test
    void codeUnitsPreferInsertableSymbolsInsteadOfLocalVariables() {
        EditUnitPlanner planner = new EditUnitPlanner(
                new TreeSitterTargetLocator(new TreeSitterSupport())
        );

        List<EditUnit> units = planner.planCodeUnits(
                Path.of("main.js"),
                """
                function generateNewPiece() {
                  const shapes = [];
                  const randomShape = shapes[0];
                  return randomShape;
                }
                """
        );

        assertEquals(1, units.size());
        assertEquals(List.of("generateNewPiece"), units.getFirst().allowedSymbols());
    }

    @Test
    void codeUnitsPreferClassMembersInsteadOfClassContainerWhenBothExist() {
        EditUnitPlanner planner = new EditUnitPlanner(
                new TreeSitterTargetLocator(new TreeSitterSupport())
        );

        List<EditUnit> units = planner.planCodeUnits(
                Path.of("index.app.js"),
                """
                class GameLogic {
                  constructor() {
                    this.board = [];
                  }

                  createBoard() {
                    return this.board;
                  }

                  start() {
                    return this.createBoard();
                  }
                }
                """
        );

        assertEquals(2, units.size());
        assertEquals(List.of("createBoard"), units.get(0).allowedSymbols());
        assertEquals(List.of("start"), units.get(1).allowedSymbols());
    }

    @Test
    void cssRulesAreSplitIntoSingleSelectorUnits() {
        EditUnitPlanner planner = new EditUnitPlanner(
                new TreeSitterTargetLocator(new TreeSitterSupport())
        );

        List<EditUnit> units = planner.planCodeUnits(
                Path.of("style.css"),
                """
                #app-root {
                  min-height: 100vh;
                }

                #game-canvas {
                  border: 4px solid #ffffff;
                }

                #ui-container {
                  display: flex;
                }

                button {
                  cursor: pointer;
                }
                """
        );

        assertEquals(4, units.size());
        assertEquals(List.of("#app-root"), units.get(0).allowedSymbols());
        assertEquals(List.of("#game-canvas"), units.get(1).allowedSymbols());
        assertEquals(List.of("#ui-container"), units.get(2).allowedSymbols());
        assertEquals(List.of("button"), units.get(3).allowedSymbols());
    }
}
