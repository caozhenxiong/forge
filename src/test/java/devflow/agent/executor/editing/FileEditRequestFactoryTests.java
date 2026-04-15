package devflow.agent.executor.editing;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.patch.PatchApplyResult;
import devflow.agent.executor.patch.PatchContextBuilder;
import devflow.agent.executor.patch.PatchTargetContext;
import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
import devflow.agent.parsing.SourceLanguage;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.executor.subtask.Subtask;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileEditRequestFactoryTests {

    @TempDir
    Path tempDir;

    @Test
    void hostEntryHtmlReceivesRuntimeContractFromFingerprintScopedEntry() throws Exception {
        java.nio.file.Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!doctype html>
                        <html>
                        <body>
                          <script type="module" src="./index.app.js"></script>
                        </body>
                        </html>
                        """
        );

        FileEditRequest request = newFactory().create(
                tempDir,
                Path.of("index.html"),
                "patch host",
                subtask("index.html"),
                null,
                "",
                "repair runtime wiring",
                null,
                null,
                fingerprint("index.html", Set.of("index.html", "index.app.js")),
                "",
                null
        );

        HtmlRuntimeOwnershipContract runtimeContract = request.runtimeContract();
        assertNotNull(runtimeContract);
        assertTrue(runtimeContract.externalCompanion());
        assertEquals(List.of("index.app.js"), runtimeContract.runtimePathStrings());
    }

    @Test
    void nonHostHtmlDoesNotReceiveRuntimeContractWhenFingerprintPointsElsewhere() throws Exception {
        java.nio.file.Files.createDirectories(tempDir.resolve("partials"));
        java.nio.file.Files.writeString(
                tempDir.resolve("partials/card.html"),
                """
                        <div class="card">
                          <script type="module" src="../index.app.js"></script>
                        </div>
                        """
        );

        FileEditRequest request = newFactory().create(
                tempDir,
                Path.of("partials/card.html"),
                "patch partial",
                subtask("partials/card.html"),
                null,
                "",
                "update partial",
                null,
                null,
                fingerprint("index.html", Set.of("index.html", "partials/card.html", "index.app.js")),
                "",
                null
        );

        assertNull(request.runtimeContract());
    }

    @Test
    void fileScopedRequestNarrowsTaskPackageOwnedFilesToCurrentFile() throws Exception {
        java.nio.file.Files.createDirectories(tempDir.resolve("src"));
        java.nio.file.Files.writeString(tempDir.resolve("src/app.js"), "export const ready = true;");

        FileEditRequest request = newFactory().create(
                tempDir,
                Path.of("src/app.js"),
                "patch current file",
                new Subtask(
                        "patch current file",
                        "patch current file",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of(
                                new FileChange("index.html", ChangeAction.WRITE, "host entry"),
                                new FileChange("src/app.js", ChangeAction.WRITE, "logic")
                        )
                ),
                new devflow.agent.executor.subtask.TaskPackage(
                        "patch current file",
                        "patch current file",
                        DeliveryMode.PATCH.name(),
                        false,
                        List.of("index.html", "src/app.js"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "shared context",
                        null
                ),
                "",
                "patch current file",
                null,
                null,
                fingerprint("index.html", Set.of("index.html", "src/app.js")),
                "",
                null
        );

        assertEquals(List.of("src/app.js"), request.taskPackage().ownedFiles());
        assertFalse(request.taskPackage().toMarkdown().contains("- index.html"));
    }

    private FileEditRequestFactory newFactory() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        TargetLocator targetLocator = (relativePath, source) -> new PatchTargetContext(
                relativePath,
                SourceLanguage.UNSUPPORTED,
                false,
                List.of(),
                List.of(),
                ""
        );
        LanguageEditAdapter languageEditAdapter = new LanguageEditAdapter() {
            @Override
            public boolean supports(Path relativePath) {
                return false;
            }

            @Override
            public boolean supportsPreciseEditing(Path relativePath, String source) {
                return false;
            }

            @Override
            public boolean supportsAppendOnlyEditing(Path relativePath, String source) {
                return false;
            }

            @Override
            public PatchTargetContext locateTargets(Path relativePath, String source) {
                return new PatchTargetContext(relativePath, SourceLanguage.UNSUPPORTED, false, List.of(), List.of(), "");
            }

            @Override
            public PatchApplyResult applyPatch(Path projectPath, Path relativePath, String currentContent, devflow.agent.editing.precise.ExactReplaceEdit edit) {
                return null;
            }
        };
        FileScopedContextSupport scopedContextSupport = new FileScopedContextSupport(
                workspace,
                new RuntimeWorkingSetResolver(),
                new PatchContextBuilder(targetLocator, languageEditAdapter),
                new TaskPackageMarkdownRenderer()
        );
        return new FileEditRequestFactory(workspace, scopedContextSupport);
    }

    private Subtask subtask(String path) {
        return new Subtask(
                "patch " + path,
                "patch " + path,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                DeliveryMode.PATCH,
                List.of(new FileChange(path, ChangeAction.WRITE, "update " + path))
        );
    }

    private ProjectFingerprint fingerprint(String htmlEntryPath, Set<String> fileNames) {
        return new ProjectFingerprint(
                "web",
                "",
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                htmlEntryPath,
                fileNames,
                List.of()
        );
    }
}
