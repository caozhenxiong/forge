package devflow.agent.orchestrator;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.loop.TransitionReason;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.domain.WorkflowAction;
import devflow.agent.supervisor.SupervisorDecision;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowControllerTests {

    @Test
    void mapsSupervisorDecisionToTransitionDecision() {
        FlowController controller = new FlowController();
        ReviewResult reviewResult = new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.ADVANCE_STAGE,
                StageType.PRD,
                FixMode.NONE,
                "继续下一阶段",
                List.of("进入 PRD"),
                List.of(),
                List.of(),
                DeliveryPolicy.patchSafe(),
                false
        );

        FlowDecision flowDecision = controller.decide(
                StageType.ANALYSIS,
                reviewResult,
                false,
                supervisorDecision,
                StageToolResultSummary.none()
        );

        assertEquals(WorkflowAction.ADVANCE_STAGE, flowDecision.action());
        assertEquals(StageType.PRD, flowDecision.targetStage());
        assertEquals(TransitionReason.STAGE_APPROVED, flowDecision.transitionDecision().reason());
        assertEquals(StageType.ANALYSIS, flowDecision.transitionDecision().fromStage());
    }

    @Test
    void shouldContinueOnlyWhenRunIsStillRunningCurrentStage() {
        FlowController controller = new FlowController();
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(StageType.ANALYSIS, new StageExecution(StageType.ANALYSIS, StageStatus.RUNNING, 1, null, null, null, null));
        RunRecord running = new RunRecord(
                UUID.randomUUID(),
                null,
                "goal",
                "",
                RunConfig.defaultConfig(),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
        assertTrue(controller.shouldContinue(running));

        states.put(StageType.ANALYSIS, new StageExecution(StageType.ANALYSIS, StageStatus.AWAITING_HUMAN_REVIEW, 1, null, null, null, null));
        RunRecord blocked = running.withCurrentStage(StageType.ANALYSIS, RunStatus.BLOCKED, states, Instant.now());
        assertFalse(controller.shouldContinue(blocked));

        EnumMap<StageType, StageExecution> missingStates = new EnumMap<>(states);
        missingStates.remove(StageType.ANALYSIS);
        RunRecord missingCurrentStage = new RunRecord(
                UUID.randomUUID(),
                null,
                "goal",
                "",
                RunConfig.defaultConfig(),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                missingStates,
                Instant.now(),
                Instant.now()
        );
        assertFalse(controller.shouldContinue(missingCurrentStage));
    }

    @Test
    void blockingToolFailuresForceRetryOfCurrentStage() {
        FlowController controller = new FlowController();
        ReviewResult reviewResult = new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.COMPLETE_RUN,
                null,
                FixMode.NONE,
                "全部完成",
                List.of(),
                List.of(),
                List.of(),
                DeliveryPolicy.patchSafe(),
                false
        );

        FlowDecision flowDecision = controller.decide(
                StageType.TEST,
                reviewResult,
                false,
                supervisorDecision,
                new StageToolResultSummary(
                        true,
                        1,
                        List.of("TEST_CASE_EXECUTION"),
                        List.of("TEST_CASE_EXECUTION_FAILED"),
                        "tool results still failed",
                        "case execution failed",
                        "rerun current stage"
                )
        );

        assertEquals(WorkflowAction.RETRY_STAGE, flowDecision.action());
        assertEquals(StageType.TEST, flowDecision.targetStage());
        assertEquals(TransitionReason.STAGE_RETRY, flowDecision.transitionDecision().reason());
    }
}
