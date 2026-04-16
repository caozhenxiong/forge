package devflow.agent.interfaceadapter.cli;

import devflow.agent.domain.HumanReviewResolutionContext;
import devflow.agent.domain.GatePolicy;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.orchestrator.TerminalHumanApprovalRejectedException;
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
                HumanReviewResolutionContext.approveStageGate("ok", ""),
                Instant.now(),
                Instant.now()
        );

        String summary = renderer.renderSummary(runRecord);

        assertTrue(summary.contains("runId:"));
        assertTrue(summary.contains("currentStage: DESIGN"));
        assertTrue(summary.contains("review=APPROVED"));
        assertTrue(summary.contains("artifact=design.md"));
        assertTrue(summary.contains("humanReview: intent=APPROVE_STAGE_GATE"));
    }

    @Test
    void rendersTerminalApprovalRejectedDiagnostic() {
        Map<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        stageStates.put(
                StageType.TEST,
                new StageExecution(StageType.TEST, StageStatus.AWAITING_HUMAN_REVIEW, 1, "test.md", ReviewDecision.REJECTED, "blocked", "repair")
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
        RunRecord runRecord = new RunRecord(
                UUID.randomUUID(),
                Path.of("/tmp/demo"),
                "build a game",
                "pure web",
                RunConfig.defaultConfig(),
                StageType.TEST,
                RunStatus.BLOCKED,
                stageStates,
                context,
                Instant.now(),
                Instant.now()
        );

        String rendered = renderer.renderTerminalApprovalRejected(
                new TerminalHumanApprovalRejectedException(runRecord, StageType.TEST, context, "autopilot")
        );

        assertTrue(rendered.contains("terminalApprovalRejected: true"));
        assertTrue(rendered.contains("stage: TEST"));
        assertTrue(rendered.contains("message: terminal diagnostic"));
    }
}
