package devflow.agent.executor.editing;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.subtask.Subtask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPackageMarkdownRendererTests {

    @TempDir
    Path tempDir;

    @Test
    void renderCompactIncludesCanonicalBoundaryContract() {
        TaskPackageMarkdownRenderer renderer = new TaskPackageMarkdownRenderer();
        Subtask subtask = new Subtask(
                "build shell",
                "only build the shell",
                List.of("CAP-1"),
                List.of("shell"),
                List.of("gameplay"),
                List.of("页面可打开"),
                true,
                DeliveryMode.PATCH,
                List.of(new FileChange("index.html", ChangeAction.WRITE, "host entry"))
        );

        String markdown = renderer.renderCompact(
                Path.of("."),
                subtask,
                new SharedContextBundle("goal", "constraints", null, List.of(), List.of(), List.of(), "", ""),
                null,
                null,
                Path.of("index.html"),
                (projectPath, changes, relativePath, contractView, fingerprint) -> "ctx"
        );

        assertTrue(markdown.contains("Task Package"));
        assertTrue(markdown.contains("Owned Capabilities"));
        assertTrue(markdown.contains("Deferred Capabilities"));
        assertTrue(markdown.contains("Boundary Contract Reminder"));
    }

    @Test
    void renderCompactScopesOwnedFilesToRequestedFile() {
        TaskPackageMarkdownRenderer renderer = new TaskPackageMarkdownRenderer();
        Subtask subtask = new Subtask(
                "patch current file",
                "only patch current file",
                List.of("CAP-1"),
                List.of("shell"),
                List.of("gameplay"),
                List.of("页面可打开"),
                true,
                DeliveryMode.PATCH,
                List.of(
                        new FileChange("index.html", ChangeAction.WRITE, "patch host"),
                        new FileChange("src/app.js", ChangeAction.WRITE, "patch logic")
                )
        );

        String markdown = renderer.renderCompact(
                Path.of("."),
                subtask,
                new SharedContextBundle("goal", "constraints", null, List.of(), List.of(), List.of(), "", ""),
                null,
                null,
                Path.of("src/app.js"),
                (projectPath, changes, relativePath, contractView, fingerprint) -> "ctx"
        );

        assertTrue(markdown.contains("- src/app.js"));
        assertFalse(markdown.contains("- index.html"));
    }

    @Test
    void renderCompactShowsPatchExistingForAlreadyCreatedFile() throws Exception {
        TaskPackageMarkdownRenderer renderer = new TaskPackageMarkdownRenderer();
        Files.writeString(tempDir.resolve("index.html"), "<!DOCTYPE html><title>ready</title>");
        Subtask subtask = new Subtask(
                "patch host",
                "patch existing host",
                List.of("CAP-1"),
                List.of("shell"),
                List.of(),
                List.of("页面可打开"),
                true,
                DeliveryMode.PATCH,
                List.of(new FileChange("index.html", ChangeAction.WRITE, "patch existing host"))
        );

        String markdown = renderer.renderCompact(
                tempDir,
                subtask,
                new SharedContextBundle("goal", "constraints", null, List.of(), List.of(), List.of(), "", ""),
                null,
                null,
                Path.of("index.html"),
                (projectPath, changes, relativePath, contractView, fingerprint) -> "ctx"
        );

        assertTrue(markdown.contains("Current File Contracts"));
        assertTrue(markdown.contains("contract=patch-existing"));
        assertFalse(markdown.contains("contract=create-new"));
    }
}
