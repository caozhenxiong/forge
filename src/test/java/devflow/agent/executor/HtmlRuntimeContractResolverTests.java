package devflow.agent.executor;

import devflow.agent.executor.editing.FileEditScope;
import devflow.agent.executor.runtime.HtmlRuntimeContractResolver;
import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
import devflow.agent.executor.runtime.RuntimeOwnershipMode;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlRuntimeContractResolverTests {

    @TempDir
    Path tempDir;

    @Test
    void scopeContractUsesDeclaredRuntimeRootsBeforeWorkspaceFacts() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/app.js"), "export const ready = true;\n");
        Files.writeString(tempDir.resolve("index.html"), "<!doctype html><html><body></body></html>");

        HtmlRuntimeContractResolver resolver = new HtmlRuntimeContractResolver(new FileProjectWorkspace());
        HtmlRuntimeOwnershipContract contract = resolver.resolveCanonicalContract(
                tempDir,
                Path.of("index.html"),
                null,
                null,
                List.of(
                        new FileChange(
                                "index.html",
                                ChangeAction.WRITE,
                                "patch host entry",
                                FileEditScope.HOST_HTML_PATCH,
                                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                true
                        ),
                        new FileChange("src/app.js", ChangeAction.WRITE, "own runtime root")
                ),
                null,
                List.of()
        );

        assertTrue(contract.externalCompanion());
        assertEquals(List.of("src/app.js"), contract.runtimePathStrings());
    }

    @Test
    void explicitInlineHostWinsOverReferencedRuntimeFacts() {
        HtmlRuntimeContractResolver resolver = new HtmlRuntimeContractResolver(new FileProjectWorkspace());

        HtmlRuntimeOwnershipContract contract = resolver.resolveCanonicalContract(
                tempDir,
                Path.of("index.html"),
                null,
                HtmlRuntimeOwnershipContract.inlineHost(Path.of("index.html")),
                List.of(),
                """
                        <!doctype html>
                        <html>
                        <body>
                          <script type="module" src="./index.app.js"></script>
                        </body>
                        </html>
                        """,
                List.of(Path.of("index.app.js"))
        );

        assertTrue(contract.inlineHost());
        assertTrue(contract.runtimePaths().isEmpty());
    }

    @Test
    void resolvedHostEntryUsesObservedRuntimeFactsWithoutScanningSiblingRoots() throws Exception {
        Files.writeString(tempDir.resolve("index.app.js"), "export const app = true;\n");
        Files.writeString(tempDir.resolve("admin.app.js"), "export const admin = true;\n");
        HtmlRuntimeContractResolver resolver = new HtmlRuntimeContractResolver(new FileProjectWorkspace());

        HtmlRuntimeOwnershipContract contract = resolver.resolveCanonicalContract(
                tempDir,
                Path.of("index.html"),
                Path.of("index.html"),
                null,
                List.of(),
                """
                        <!doctype html>
                        <html>
                        <body>
                          <script type="module" src="./index.app.js"></script>
                        </body>
                        </html>
                        """,
                List.of()
        );

        assertTrue(contract.externalCompanion());
        assertEquals(List.of("index.app.js"), contract.runtimePathStrings());
    }

    @Test
    void nonHostHtmlDoesNotInferRuntimeContractFromObservedFacts() {
        HtmlRuntimeContractResolver resolver = new HtmlRuntimeContractResolver(new FileProjectWorkspace());

        HtmlRuntimeOwnershipContract contract = resolver.resolveCanonicalContract(
                tempDir,
                Path.of("partials/card.html"),
                null,
                null,
                List.of(),
                """
                        <div class="card">
                          <script type="module" src="../index.app.js"></script>
                        </div>
                        """,
                List.of()
        );

        assertNull(contract);
    }
}
