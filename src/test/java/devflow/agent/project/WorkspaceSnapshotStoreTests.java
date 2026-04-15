package devflow.agent.project;

import devflow.agent.orchestrator.FileRunRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSnapshotStoreTests {

    @TempDir
    Path tempDir;

    @Test
    void reviewChangePackListsAllChangedPathsAndMarksTruncation() throws Exception {
        Path projectPath = tempDir.resolve("project");
        Files.createDirectories(projectPath);
        Files.writeString(projectPath.resolve("a.js"), "export const a = 1;\n");
        Files.writeString(projectPath.resolve("b.js"), "export const b = 1;\n");
        Files.writeString(projectPath.resolve("c.js"), "export const c = 1;\n");

        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(new FileRunRepository(), new FileProjectWorkspace());
        UUID runId = UUID.randomUUID();
        snapshotStore.captureBaseline(projectPath, runId);

        String largePayload = "x".repeat(9_000);
        Files.writeString(projectPath.resolve("a.js"), "export const a = '" + largePayload + "';\n");
        Files.writeString(projectPath.resolve("b.js"), "export const b = '" + largePayload + "';\n");
        Files.writeString(projectPath.resolve("c.js"), "export const c = '" + largePayload + "';\n");

        String markdown = snapshotStore.buildReviewChangePack(projectPath, runId).toMarkdown();
        assertTrue(markdown.contains("MODIFIED: a.js"));
        assertTrue(markdown.contains("MODIFIED: b.js"));
        assertTrue(markdown.contains("MODIFIED: c.js"));
        assertTrue(markdown.contains("- truncated: true"));
        assertTrue(markdown.contains("## File Excerpts"));
    }
}
