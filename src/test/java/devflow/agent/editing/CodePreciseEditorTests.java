package devflow.agent.editing;

import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodePreciseEditorTests {

    private final CodePreciseEditor editor = new CodePreciseEditor(new TreeSitterSupport());

    @Test
    void appliesPrecisePatchToJavaSymbols() {
        String source = """
                class App {
                    void tick() {
                        System.out.println("old");
                    }
                }
                """;

        String updated = editor.applyPatch(
                Path.of("App.java"),
                source,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL,
                                "tick",
                                "method",
                                """
                                void tick() {
                                    System.out.println("new");
                                }
                                """
                        )
                ))
        );

        assertTrue(updated.contains("System.out.println(\"new\");"));
        assertTrue(updated.contains("class App"));
    }

    @Test
    void appliesPrecisePatchToPythonFunctions() {
        String source = """
                class Game:
                    def tick(self):
                        return 1
                """;

        String updated = editor.applyPatch(
                Path.of("game.py"),
                source,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL,
                                "tick",
                                "function",
                                """
                                def tick(self):
                                    return 2
                                """
                        )
                ))
        );

        assertTrue(updated.contains("return 2"));
        assertTrue(updated.contains("class Game:"));
    }

    @Test
    void appliesPrecisePatchToGoFunctions() {
        String source = """
                package main

                func main() {
                    println("old")
                }
                """;

        String updated = editor.applyPatch(
                Path.of("main.go"),
                source,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL,
                                "main",
                                "function",
                                """
                                func main() {
                                    println("new")
                                }
                                """
                        )
                ))
        );

        assertTrue(updated.contains("println(\"new\")"));
        assertTrue(updated.contains("package main"));
    }

    @Test
    void appliesPrecisePatchToJavaScriptSymbols() {
        String source = """
                class Game {
                    tick() {
                        return 1;
                    }
                }
                """;

        String updated = editor.applyPatch(
                Path.of("game.js"),
                source,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL,
                                "tick",
                                "method",
                                """
                                tick() {
                                    return 2;
                                }
                                """
                        )
                ))
        );

        assertTrue(updated.contains("return 2;"));
        assertTrue(updated.contains("class Game"));
    }

    @Test
    void appliesPrecisePatchToTypeScriptSymbols() {
        String source = """
                function boot(): void {
                    console.log("old");
                }
                """;

        String updated = editor.applyPatch(
                Path.of("app.ts"),
                source,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL,
                                "boot",
                                "function",
                                """
                                function boot(): void {
                                    console.log("new");
                                }
                                """
                        )
                ))
        );

        assertTrue(updated.contains("console.log(\"new\");"));
        assertTrue(updated.contains("function boot(): void"));
    }

    @Test
    void replacesOnlyJavascriptFunctionBodyWithoutTouchingExportSignature() {
        String source = """
                export function checkLines(board) {
                    return 0;
                }
                """;

        String updated = editor.applyPatch(
                Path.of("gameLogic.js"),
                source,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL_BODY,
                                "checkLines",
                                "function",
                                """
                                const cleared = [];
                                return cleared.length;
                                """
                        )
                ))
        );

        assertTrue(updated.contains("export function checkLines(board) {"));
        assertTrue(updated.contains("const cleared = [];"));
        assertTrue(updated.contains("return cleared.length;"));
    }

    @Test
    void resolvesContentFromContentLinesWhenPresent() {
        String source = """
                export function checkLines(board) {
                    return 0;
                }
                """;

        String updated = editor.applyPatch(
                Path.of("gameLogic.js"),
                source,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL_BODY,
                                "checkLines",
                                "function",
                                null,
                                List.of("const cleared = [];", "return cleared.length;")
                        )
                ))
        );

        assertTrue(updated.contains("const cleared = [];"));
        assertTrue(updated.contains("return cleared.length;"));
    }

    @Test
    void normalizesTargetKindToExistingSymbolKindWhenNameUniquelyMatches() {
        String source = """
                class PieceFactory {
                    static createPiece() {
                        return {};
                    }
                }
                """;

        CodePrecisePatch normalized = editor.normalizePatchTargets(
                Path.of("piece-factory.js"),
                source,
                new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL_BODY,
                                "createPiece",
                                "function",
                                null,
                                List.of("return { type: 'I' };")
                        )
                ))
        );

        assertEquals("method", normalized.operations().getFirst().targetKind());
        String updated = editor.applyPatch(Path.of("piece-factory.js"), source, normalized);
        assertTrue(updated.contains("return { type: 'I' };"));
    }
}
