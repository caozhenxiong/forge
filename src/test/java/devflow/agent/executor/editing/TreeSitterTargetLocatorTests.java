package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeSitterTargetLocatorTests {

    private final TargetLocator targetLocator = new TreeSitterTargetLocator(new TreeSitterSupport());

    @Test
    void javaScriptTargetsAreDiscoveredFromStableSymbols() {
        PatchTargetContext context = targetLocator.locate(
                Path.of("game.js"),
                """
                export function tick() {
                  return 1;
                }

                export function render() {
                  return tick();
                }
                """
        );

        assertTrue(context.preciseEditingSupported());
        assertTrue(context.targetNames().contains("tick"));
        assertTrue(context.insertableTargetNames().contains("render"));
    }

    @Test
    void pythonTargetsReuseSameLocatorContract() {
        PatchTargetContext context = targetLocator.locate(
                Path.of("game.py"),
                """
                def tick():
                    return 1

                def render():
                    return tick()
                """
        );

        assertTrue(context.preciseEditingSupported());
        assertTrue(context.targetNames().contains("tick"));
        assertFalse(context.targetSummary().isBlank());
    }

    @Test
    void cssTargetsReuseSameLocatorContract() {
        PatchTargetContext context = targetLocator.locate(
                Path.of("style.css"),
                """
                #app {
                  color: red;
                }

                .panel {
                  display: flex;
                }
                """
        );

        assertTrue(context.preciseEditingSupported());
        assertTrue(context.targetNames().contains("#app"));
        assertTrue(context.insertableTargetNames().contains(".panel"));
    }

    @Test
    void javascriptInsertableTargetsPreferMemberSymbolsOverClassContainer() {
        PatchTargetContext context = targetLocator.locate(
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

        assertTrue(context.preciseEditingSupported());
        assertTrue(context.targetNames().contains("GameLogic"));
        assertEquals(
                java.util.List.of("createBoard", "start"),
                context.insertableTargetNames()
        );
    }

    @Test
    void javascriptKeepsClassContainerWhenOnlyConstructorExists() {
        PatchTargetContext context = targetLocator.locate(
                Path.of("index.app.js"),
                """
                class GameLogic {
                  constructor() {
                    this.board = [];
                  }
                }
                """
        );

        assertTrue(context.preciseEditingSupported());
        assertTrue(context.insertableTargetNames().contains("GameLogic"));
        assertFalse(context.insertableTargetNames().contains("constructor"));
    }

    @Test
    void javascriptOrdersClassMembersAheadOfTopLevelFunctions() {
        PatchTargetContext context = targetLocator.locate(
                Path.of("main.js"),
                """
                function main() {
                  return new Game();
                }

                class Game {
                  constructor() {
                    this.running = false;
                  }

                  setupEventListeners() {
                  }

                  handleInput() {
                  }
                }
                """
        );

        assertEquals(
                java.util.List.of("setupEventListeners", "handleInput", "main"),
                context.insertableTargetNames()
        );
    }
}
