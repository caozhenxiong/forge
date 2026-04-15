package devflow.agent.executor.editing;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.subtask.Subtask;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPackageMarkdownRendererTests {

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
}
