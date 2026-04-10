package devflow.agent.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevflowPathSupportTests {

    @Test
    void buildsStableRunAndTransactionPaths() {
        Path projectPath = Path.of("/tmp/demo-project");
        UUID runId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertEquals(
                projectPath.resolve(".devflow"),
                DevflowPathSupport.workspaceRoot(projectPath)
        );
        assertEquals(
                projectPath.resolve(".devflow").resolve("runs"),
                DevflowPathSupport.runsRoot(projectPath)
        );
        assertEquals(
                projectPath.resolve(".devflow").resolve("runs").resolve(runId.toString()),
                DevflowPathSupport.runDirectory(projectPath, runId)
        );
        assertEquals(
                projectPath.resolve(".devflow").resolve("runs").resolve(runId.toString()).resolve("baseline"),
                DevflowPathSupport.baselineRoot(projectPath, runId)
        );
        assertEquals(
                projectPath.resolve(".devflow").resolve("write-transactions").resolve("active").resolve("txn-1"),
                DevflowPathSupport.writeTransactionStagingRoot(projectPath, "txn-1")
        );
    }
}
