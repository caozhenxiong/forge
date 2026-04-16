package devflow.agent.orchestrator;

import devflow.agent.domain.HumanReviewResolutionContext;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void approveStageRejectsTerminalHumanStateWithoutMutatingRunRecord() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageStatusSupport stageStatusSupport = new StageStatusSupport(
                runRepository,
                new devflow.agent.artifact.FileArtifactStore(runRepository),
                new EventLogStore(runRepository),
                new StageFlowPolicy(),
                new WorkflowArtifactRenderer()
        );
        WorkflowRunLifecycleSupport support = new WorkflowRunLifecycleSupport(
                runRepository,
                new EventLogStore(runRepository),
                new WorkspaceSnapshotStore(runRepository, new devflow.agent.project.FileProjectWorkspace()),
                new StageTransitionSupport(stageStatusSupport, null, null),
                new StageEntryExecutor(
                        runRepository,
                        new devflow.agent.artifact.FileArtifactStore(runRepository),
                        new EventLogStore(runRepository),
                        null,
                        new StageTransitionSupport(stageStatusSupport, null, null)
                ),
                null,
                null
        );
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        stageStates.put(
                StageType.TEST,
                new StageExecution(StageType.TEST, StageStatus.AWAITING_HUMAN_REVIEW, 1, null, null, "blocked", "repair route")
        );
        HumanReviewResolutionContext context = HumanReviewResolutionContext.confirmRepairRoute(
                StageType.IMPLEMENTATION,
                devflow.agent.review.FixMode.PATCH,
                devflow.agent.review.ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                java.util.List.of(),
                "summary",
                "change",
                "evidence",
                "actions",
                devflow.agent.protocol.ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK
        ).asTerminal("terminal diagnostic");
        RunRecord runRecord = runRepository.save(
                new RunRecord(
                        UUID.randomUUID(),
                        tempDir,
                        "goal",
                        "",
                        devflow.agent.domain.RunConfig.defaultConfig(),
                        StageType.TEST,
                        RunStatus.BLOCKED,
                        stageStates,
                        context,
                        Instant.now(),
                        Instant.now()
                )
        );

        assertThrows(
                TerminalHumanApprovalRejectedException.class,
                () -> support.approveStage(tempDir, runRecord.runId(), StageType.TEST, "tester")
        );

        RunRecord reloaded = runRepository.findById(tempDir, runRecord.runId()).orElseThrow();
        assertEquals(RunStatus.BLOCKED, reloaded.status());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, reloaded.stageStates().get(StageType.TEST).status());
        assertEquals(context, reloaded.humanReviewResolutionContext());
    }
}
