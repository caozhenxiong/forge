package devflow.agent.interfaceadapter.cli;

import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.ReviewDecision;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CliOutputRendererTests {

    private final CliOutputRenderer renderer = new CliOutputRenderer();

    @Test
    void rendersRunSummaryWithReviewMetadata() {
        Map<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        stageStates.put(
                StageType.DESIGN,
                new StageExecution(StageType.DESIGN, StageStatus.AWAITING_HUMAN_REVIEW, 2, "design.md", ReviewDecision.APPROVED, "ok", "")
        );
        RunRecord runRecord = new RunRecord(
                UUID.randomUUID(),
                Path.of("/tmp/demo"),
                "build a game",
                "pure web",
                RunConfig.defaultConfig(),
                StageType.DESIGN,
                RunStatus.BLOCKED,
                stageStates,
                Instant.now(),
                Instant.now()
        );

        String summary = renderer.renderSummary(runRecord);

        assertTrue(summary.contains("runId:"));
        assertTrue(summary.contains("currentStage: DESIGN"));
        assertTrue(summary.contains("review=APPROVED"));
        assertTrue(summary.contains("artifact=design.md"));
    }
}
