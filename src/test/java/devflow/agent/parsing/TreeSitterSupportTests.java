package devflow.agent.parsing;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSitterSupportTests {

    private final TreeSitterSupport support = new TreeSitterSupport();

    @Test
    void htmlSnapshotExtractsStaticSelectorsAndCanvas() {
        HtmlStructureSnapshot snapshot = support.inspectHtml("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <body>
                  <button id="startBtn" class="primary">开始</button>
                  <button class="ghost secondary">暂停</button>
                  <canvas id="gameCanvas"></canvas>
                </body>
                </html>
                """);

        assertTrue(snapshot.parseSummary().valid());
        assertTrue(snapshot.hasHtmlRoot());
        assertTrue(snapshot.hasBody());
        assertTrue(snapshot.hasCanvas());
        assertTrue(snapshot.idSelectors().contains("#startBtn"));
        assertTrue(snapshot.buttonSelectors().contains("#startBtn"));
        assertTrue(snapshot.buttonSelectors().contains(".ghost"));
    }

    @Test
    void detectsMalformedHtmlStructure() {
        TreeSitterParseSummary summary = support.analyze(
                Path.of("index.html"),
                """
                <!DOCTYPE html>
                <html>
                <body>
                  <div id="a"><span
                </body>
                </html>
                """
        );

        assertFalse(summary.valid());
    }

    @Test
    void detectsMalformedJavaScriptStructure() {
        TreeSitterParseSummary summary = support.analyze(
                Path.of("app.js"),
                "function broken( { return 1;"
        );

        assertFalse(summary.valid());
    }

    @Test
    void extractsPreciseEditingSymbolsForJavaPythonAndGo() {
        CodeStructureSnapshot javaSnapshot = support.inspectCodeStructure(
                Path.of("App.java"),
                """
                class App {
                    App() {}
                    void tick() {}
                }
                """
        );
        CodeStructureSnapshot pythonSnapshot = support.inspectCodeStructure(
                Path.of("game.py"),
                """
                class Game:
                    def tick(self):
                        return 1
                """
        );
        CodeStructureSnapshot goSnapshot = support.inspectCodeStructure(
                Path.of("game.go"),
                """
                package main

                type Game struct {
                }

                func (g *Game) Tick() {
                }
                """
        );

        assertTrue(javaSnapshot.supportsPreciseEditing());
        assertTrue(pythonSnapshot.supportsPreciseEditing());
        assertTrue(goSnapshot.supportsPreciseEditing());
        assertEquals("class", javaSnapshot.symbols().getFirst().kind());
        assertTrue(javaSnapshot.symbols().stream().anyMatch(symbol -> "tick".equals(symbol.name()) && "method".equals(symbol.kind())));
        assertTrue(pythonSnapshot.symbols().stream().anyMatch(symbol -> "Game".equals(symbol.name()) && "class".equals(symbol.kind())));
        assertTrue(pythonSnapshot.symbols().stream().anyMatch(symbol -> "tick".equals(symbol.name()) && "function".equals(symbol.kind())));
        assertTrue(goSnapshot.symbols().stream().anyMatch(symbol -> "Game".equals(symbol.name()) && "type".equals(symbol.kind())));
        assertTrue(goSnapshot.symbols().stream().anyMatch(symbol -> "Tick".equals(symbol.name()) && "method".equals(symbol.kind())));
    }

    @Test
    void extractsPreciseEditingSymbolsForJavaScriptAndTypeScript() {
        CodeStructureSnapshot javascriptSnapshot = support.inspectCodeStructure(
                Path.of("app.js"),
                """
                class Game {
                    tick() {
                        const drawX = 1;
                    }
                }

                function boot() {
                    const drawY = 2;
                }
                const score = 0;
                """
        );
        CodeStructureSnapshot typescriptSnapshot = support.inspectCodeStructure(
                Path.of("app.ts"),
                """
                class Game {
                    tick(): void {}
                }

                function boot(): void {}
                const score: number = 0;
                """
        );
        CodeStructureSnapshot tsxSnapshot = support.inspectCodeStructure(
                Path.of("app.tsx"),
                """
                export function App(): JSX.Element {
                    return <main>ok</main>;
                }
                """
        );

        assertTrue(javascriptSnapshot.supportsPreciseEditing());
        assertTrue(typescriptSnapshot.supportsPreciseEditing());
        assertFalse(tsxSnapshot.supportsPreciseEditing());
        assertTrue(javascriptSnapshot.symbols().stream().anyMatch(symbol -> "Game".equals(symbol.name()) && "class".equals(symbol.kind())));
        assertTrue(javascriptSnapshot.symbols().stream().anyMatch(symbol -> "tick".equals(symbol.name()) && "method".equals(symbol.kind())));
        assertTrue(javascriptSnapshot.symbols().stream().anyMatch(symbol -> "boot".equals(symbol.name()) && "function".equals(symbol.kind())));
        assertTrue(javascriptSnapshot.symbols().stream().anyMatch(symbol -> "score".equals(symbol.name()) && "variable".equals(symbol.kind())));
        assertFalse(javascriptSnapshot.symbols().stream().anyMatch(symbol -> "drawX".equals(symbol.name())));
        assertFalse(javascriptSnapshot.symbols().stream().anyMatch(symbol -> "drawY".equals(symbol.name())));
        assertTrue(typescriptSnapshot.symbols().stream().anyMatch(symbol -> "Game".equals(symbol.name()) && "class".equals(symbol.kind())));
        assertTrue(typescriptSnapshot.symbols().stream().anyMatch(symbol -> "boot".equals(symbol.name()) && "function".equals(symbol.kind())));
        assertTrue(typescriptSnapshot.symbols().stream().anyMatch(symbol -> "score".equals(symbol.name()) && "variable".equals(symbol.kind())));
        assertTrue(tsxSnapshot.symbols().isEmpty());
    }

    @Test
    void malformedTsxDoesNotInventHeuristicSymbols() {
        CodeStructureSnapshot malformedTsxSnapshot = support.inspectCodeStructure(
                Path.of("broken.tsx"),
                """
                export function App(): JSX.Element {
                    return <main>
                }
                """
        );

        assertFalse(malformedTsxSnapshot.parseSummary().valid());
        assertTrue(malformedTsxSnapshot.symbols().isEmpty());
        assertFalse(malformedTsxSnapshot.supportsPreciseEditing());
    }
}
