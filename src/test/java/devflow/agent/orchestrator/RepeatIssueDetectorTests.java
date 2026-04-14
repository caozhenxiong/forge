package devflow.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatIssueDetectorTests {

    @TempDir
    Path tempDir;

    @Test
    void approvedReviewDoesNotInvokeDiagnosis() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        AtomicBoolean diagnosisCalled = new AtomicBoolean(false);
        RepeatIssueDetector detector = new RepeatIssueDetector(diagnosisAgent(artifactStore, diagnosisCalled, true));

        boolean repeated = detector.shouldDiagnose(
                tempDir,
                runRecord(StageType.IMPLEMENTATION),
                StageType.IMPLEMENTATION,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "")
        );

        assertFalse(repeated);
        assertFalse(diagnosisCalled.get());
    }

    @Test
    void rejectedNonAnalysisReviewDelegatesToDiagnosis() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        AtomicBoolean diagnosisCalled = new AtomicBoolean(false);
        RepeatIssueDetector detector = new RepeatIssueDetector(diagnosisAgent(artifactStore, diagnosisCalled, true));

        boolean repeated = detector.shouldDiagnose(
                tempDir,
                runRecord(StageType.CODE_REVIEW),
                StageType.CODE_REVIEW,
                new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "same issue", "fix it")
        );

        assertTrue(repeated);
        assertTrue(diagnosisCalled.get());
    }

    private DiagnosisAgent diagnosisAgent(FileArtifactStore artifactStore, AtomicBoolean diagnosisCalled, boolean result) {
        return new DiagnosisAgent(null, artifactStore, new ObjectMapper()) {
            @Override
            public boolean shouldDiagnose(
                    Path projectPath,
                    RunRecord runRecord,
                    StageType stageType,
                    FixMode requestedMode,
                    String summary,
                    String changeRequest
            ) {
                diagnosisCalled.set(true);
                return result;
            }
        };
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
