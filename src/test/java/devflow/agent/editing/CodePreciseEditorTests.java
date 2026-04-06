package devflow.agent.editing;

import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
