package devflow.agent.executor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationMutationContractGuardTests {

    @TempDir
    Path tempDir;

    private final ImplementationMutationContractGuard guard = new ImplementationMutationContractGuard();

    @Test
    void rejectsUndeclaredHtmlRuntimeDependency() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validate(
                        tempDir,
                        Path.of("index.html"),
                        """
                                <!doctype html>
                                <html>
                                <head>
                                  <script src="src/script.js"></script>
                                </head>
                                <body></body>
                                </html>
                                """,
                        Set.of(Path.of("index.html"), Path.of("src/game.js")),
                        List.of(
                                new FileChange(
                                        "index.html",
                                        ChangeAction.WRITE,
                                        "更新入口",
                                        FileEditScope.HOST_HTML_PATCH,
                                        RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                        true
                                ),
                                new FileChange("src/game.js", ChangeAction.WRITE, "补齐主运行时")
                        )
                )
        );

        assertTrue(exception.getMessage().contains("未声明的本地依赖路径"));
        assertTrue(exception.getMessage().contains("src/script.js"));
    }

    @Test
    void allowsHtmlReferenceToOwnedRuntimeFileBeforeItExists() {
        assertDoesNotThrow(() -> guard.validate(
                tempDir,
                Path.of("index.html"),
                """
                        <!doctype html>
                        <html>
                        <head>
                          <script type="module" src="src/game.js"></script>
                        </head>
                        <body></body>
                        </html>
                        """,
                Set.of(Path.of("index.html"), Path.of("src/game.js")),
                List.of(
                        new FileChange(
                                "index.html",
                                ChangeAction.WRITE,
                                "更新入口",
                                FileEditScope.HOST_HTML_PATCH,
                                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                true
                        ),
                        new FileChange("src/game.js", ChangeAction.WRITE, "补齐主运行时")
                )
        ));
    }

    @Test
    void rejectsExternalCompanionHtmlThatKeepsInlineMainScript() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validate(
                        tempDir,
                        Path.of("index.html"),
                        """
                                <!doctype html>
                                <html>
                                <body>
                                  <script src="src/game.js"></script>
                                  <script>
                                    console.log('inline main runtime');
                                  </script>
                                </body>
                                </html>
                                """,
                        Set.of(Path.of("index.html"), Path.of("src/game.js")),
                        List.of(
                                new FileChange(
                                        "index.html",
                                        ChangeAction.WRITE,
                                        "更新入口",
                                        FileEditScope.HOST_HTML_PATCH,
                                        RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                        true
                                ),
                                new FileChange("src/game.js", ChangeAction.WRITE, "补齐主运行时")
                        )
                )
        );

        assertTrue(exception.getMessage().contains("EXTERNAL_COMPANION"));
        assertTrue(exception.getMessage().contains("内联主脚本"));
    }

    @Test
    void allowsInlineHostHtmlWithoutCompanionRuntimeFiles() throws Exception {
        Files.createDirectories(tempDir.resolve("assets"));
        Files.writeString(tempDir.resolve("assets/theme.css"), "body { color: #111; }");

        assertDoesNotThrow(() -> guard.validate(
                tempDir,
                Path.of("index.html"),
                """
                        <!doctype html>
                        <html>
                        <head>
                          <link rel="stylesheet" href="assets/theme.css">
                        </head>
                        <body>
                          <script>
                            console.log('inline host runtime');
                          </script>
                        </body>
                        </html>
                        """,
                Set.of(Path.of("index.html")),
                List.of(new FileChange(
                        "index.html",
                        ChangeAction.WRITE,
                        "更新入口",
                        FileEditScope.HOST_HTML_PATCH,
                        RuntimeOwnershipMode.INLINE_HOST,
                        true
                ))
        ));
    }
}
