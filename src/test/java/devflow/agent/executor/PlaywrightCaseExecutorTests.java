package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.CommandResult;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlaywrightCaseExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void executeCleansUpTempFileWhenExecutorThrows() throws Exception {
        ThrowingWorkspace workspace = new ThrowingWorkspace();
        PlaywrightCaseExecutor executor = new PlaywrightCaseExecutor(workspace, new ObjectMapper());

        executor.execute(tempDir, new TestCasePlan(
                "summary",
                List.of(new TestCaseSpec(
                        "TC-001",
                        "smoke",
                        "smoke",
                        true,
                        "index.html",
                        "",
                        "",
                        List.of(new TestStepSpec("ASSERT_NO_ERRORS", null, null, null, null, null, false))
                ))
        ));

        assertNotNull(workspace.lastTempFile);
        assertFalse(Files.exists(workspace.lastTempFile));
    }

    @Test
    void captureRuntimeSnapshotCleansUpTempFileWhenExecutorThrows() throws Exception {
        ThrowingWorkspace workspace = new ThrowingWorkspace();
        PlaywrightCaseExecutor executor = new PlaywrightCaseExecutor(workspace, new ObjectMapper());

        executor.captureRuntimeSnapshot(tempDir, "index.html");

        assertNotNull(workspace.lastTempFile);
        assertFalse(Files.exists(workspace.lastTempFile));
    }

    private static final class ThrowingWorkspace extends FileProjectWorkspace {
        private Path lastTempFile;

        @Override
        public CommandResult runCommand(Path projectPath, List<String> command, Duration timeout) {
            int tempIndex = command.contains("--snapshot") ? 3 : 2;
            lastTempFile = Path.of(command.get(tempIndex));
            throw new IllegalStateException("boom");
        }
    }
}
