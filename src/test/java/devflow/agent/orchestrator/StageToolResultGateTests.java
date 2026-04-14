package devflow.agent.orchestrator;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.protocol.ToolResultPayload;
import devflow.agent.protocol.ToolResultsArtifactPayload;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageToolResultGateTests {

    @TempDir
    Path tempDir;

    @Test
    void nonTestStagePassesThroughWithoutStructuredToolSummary() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        StageToolResultGate gate = new StageToolResultGate(new StageToolResultLoader(artifactStore), new StageToolResultGuard());
        ReviewResult reviewed = new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");

        StageToolResultGateResult result = gate.apply(tempDir, runRecord(StageType.ANALYSIS), StageType.ANALYSIS, reviewed);

        assertEquals(reviewed, result.reviewResult());
        assertFalse(result.toolSummary().blockingFailure());
    }

    @Test
    void blockingToolFailuresDowngradeApprovedTestReview() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        RunRecord runRecord = runRecord(StageType.TEST);
        artifactStore.writeAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.TEST_EXECUTION,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.TOOL_RESULTS,
                        new ToolResultsArtifactPayload(List.of(
                                new ToolResultPayload(
                                        "TEST_CASE_EXECUTION",
                                        "FAILED",
                                        "TEST_CASE_EXECUTION_FAILED",
                                        "case execution failed",
                                        "rerun current stage"
                                )
                        ))
                )
        );
        StageToolResultGate gate = new StageToolResultGate(new StageToolResultLoader(artifactStore), new StageToolResultGuard());

        StageToolResultGateResult result = gate.apply(
                tempDir,
                runRecord,
                StageType.TEST,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "")
        );

        assertTrue(result.toolSummary().blockingFailure());
        assertEquals(ReviewDecision.REVISION_REQUIRED, result.reviewResult().decision());
        assertEquals(FixMode.PATCH, result.reviewResult().fixMode());
        assertTrue(result.reviewResult().changeRequest().contains("rerun current stage"));
    }

    private RunRecord runRecord(StageType currentStage) {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(currentStage, new StageExecution(currentStage, StageStatus.RUNNING, 1, null, null, null, null));
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "",
                RunConfig.defaultConfig(),
                currentStage,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }
}
