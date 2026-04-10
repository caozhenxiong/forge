package devflow.agent.orchestrator;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowRunLifecycleSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void createRunInitializesAnalysisStageAndCapturesBaseline() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, new devflow.agent.project.FileProjectWorkspace());
        WorkflowRunLifecycleSupport support = new WorkflowRunLifecycleSupport(
                runRepository,
                new EventLogStore(runRepository),
                snapshotStore,
                null,
                null,
                null,
                null
        );

        RunRecord runRecord = support.createRun(tempDir, "goal", "constraints");

        assertEquals(StageType.ANALYSIS, runRecord.currentStage());
        assertEquals(RunStatus.CREATED, runRecord.status());
        assertEquals(StageStatus.PENDING, runRecord.stageStates().get(StageType.ANALYSIS).status());
    }
}
