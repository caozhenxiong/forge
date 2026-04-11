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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                        List.of(new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false))
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

    @Test
    void executeUsesForgeAbsoluteScriptPathInsteadOfProjectRelativePath() {
        CapturingWorkspace workspace = new CapturingWorkspace();
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
                        List.of(new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false))
                ))
        ));

        assertNotNull(workspace.lastCommand);
        Path scriptPath = Path.of(workspace.lastCommand.get(1));
        assertTrue(scriptPath.isAbsolute(), "script path should be absolute");
        assertTrue(scriptPath.endsWith(Path.of("tools", "playwright-smoke", "run-testcases.mjs")));
    }

    private static final class ThrowingWorkspace extends FileProjectWorkspace {
        private Path lastTempFile;

        @Override
        public CommandResult runCommand(Path projectPath, List<String> command, Duration timeout) {
            int tempIndex = command.contains("--probe") ? 3 : 2;
            lastTempFile = Path.of(command.get(tempIndex));
            throw new IllegalStateException("boom");
        }
    }

    private static final class CapturingWorkspace extends FileProjectWorkspace {
        private List<String> lastCommand;

        @Override
        public CommandResult runCommand(Path projectPath, List<String> command, Duration timeout) {
            this.lastCommand = command;
            return new CommandResult(
                    0,
                    "{\"status\":\"ok\",\"probe\":null,\"errors\":[],\"cases\":[]}",
                    ""
            );
        }
    }
}
