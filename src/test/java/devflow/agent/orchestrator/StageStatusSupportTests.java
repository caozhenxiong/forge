package devflow.agent.orchestrator;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageStatusSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void blockForHumanReviewMarksCurrentStageBlocked() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageStatusSupport support = newSupport(runRepository);
        RunRecord runRecord = runRepository.save(newRunRecord(StageType.ANALYSIS, StageStatus.RUNNING, 1));

        RunRecord blocked = support.blockForHumanReview(
                runRecord,
                StageType.ANALYSIS,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "")
        );

        assertEquals(RunStatus.BLOCKED, blocked.status());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, blocked.stageStates().get(StageType.ANALYSIS).status());
    }

    @Test
    void markFatalFailureIsIdempotentAfterRunAlreadyFailed() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageStatusSupport support = newSupport(runRepository);
        RunRecord base = newRunRecord(StageType.IMPLEMENTATION, StageStatus.FAILED, 2);
        RunRecord failed = runRepository.save(
                base.withCurrentStage(StageType.IMPLEMENTATION, RunStatus.FAILED, base.stageStates(), Instant.now())
        );

        support.markFatalFailure(tempDir, failed.runId(), StageType.IMPLEMENTATION, new RuntimeException("boom"));

        RunRecord reloaded = runRepository.findById(tempDir, failed.runId()).orElseThrow();
        assertEquals(RunStatus.FAILED, reloaded.status());
        assertEquals(StageStatus.FAILED, reloaded.stageStates().get(StageType.IMPLEMENTATION).status());
    }

    @Test
    void applyMaxRevisionGuardMarksRunFailedWhenBudgetIsExhausted() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageStatusSupport support = newSupport(runRepository);
        RunRecord runRecord = runRepository.save(
                new RunRecord(
                        UUID.randomUUID(),
                        tempDir,
                        "goal",
                        "",
                        new RunConfig(Map.of(StageType.IMPLEMENTATION, devflow.agent.domain.GatePolicy.AGENT_ONLY), 1),
                        StageType.IMPLEMENTATION,
                        RunStatus.IN_PROGRESS,
                        stageStates(StageType.IMPLEMENTATION, StageStatus.RUNNING, 1),
                        Instant.now(),
                        Instant.now()
                )
        );
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = StageStatusSupport.requireStage(nextStates, StageType.IMPLEMENTATION);
        nextStates.put(
                StageType.IMPLEMENTATION,
                currentExecution.withStatus(StageStatus.NEEDS_REVISION)
                        .withReview(ReviewDecision.REVISION_REQUIRED, "summary", "change")
        );

        Optional<RunRecord> failed = support.applyMaxRevisionGuard(
                tempDir,
                runRecord,
                StageType.IMPLEMENTATION,
                currentExecution,
                nextStates
        );

        assertEquals(RunStatus.FAILED, failed.orElseThrow().status());
        assertEquals(StageStatus.FAILED, failed.orElseThrow().stageStates().get(StageType.IMPLEMENTATION).status());
    }

    private StageStatusSupport newSupport(FileRunRepository runRepository) {
        return new StageStatusSupport(
                runRepository,
                new FileArtifactStore(runRepository),
                new EventLogStore(runRepository),
                new StageFlowPolicy(),
                new WorkflowArtifactRenderer()
        );
    }

    private RunRecord newRunRecord(StageType currentStage, StageStatus currentStatus, int attempt) {
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "",
                RunConfig.defaultConfig(),
                currentStage,
                RunStatus.IN_PROGRESS,
                stageStates(currentStage, currentStatus, attempt),
                Instant.now(),
                Instant.now()
        );
    }

    private Map<StageType, StageExecution> stageStates(StageType currentStage, StageStatus currentStatus, int attempt) {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(currentStage, new StageExecution(currentStage, currentStatus, attempt, null, null, null, null));
        return states;
    }
}
