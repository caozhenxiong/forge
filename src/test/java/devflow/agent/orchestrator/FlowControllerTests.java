package devflow.agent.orchestrator;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;

import devflow.agent.loop.TransitionReason;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
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

    @Test
    void reusesImplementationContinuationAsSingleRevisionOwner() {
        FlowController controller = new FlowController();
        ReviewResult reviewResult = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.REWORK,
                "代码未满足验收",
                "继续修复 implementation"
        );
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.RETRY_STAGE,
                StageType.IMPLEMENTATION,
                FixMode.REWORK,
                "回 implementation 修复",
                List.of(),
                List.of(),
                List.of(),
                DeliveryPolicy.patchSafe(),
                false
        );

        FlowDecision flowDecision = controller.decide(
                StageType.CODE_REVIEW,
                reviewResult,
                false,
                supervisorDecision,
                StageToolResultSummary.none(),
                new ImplementationRevisionFacts(
                        false,
                        false,
                        List.of("实现行消除与计分系统"),
                        new StageContinuationContext(
                                "继续修当前子任务",
                                "只修 src/game.js",
                                "continuationSubtask=实现行消除与计分系统",
                                "继续 patch",
                                List.of(new FileChange("src/game.js", ChangeAction.WRITE, "修复计分逻辑")),
                                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                                devflow.agent.review.ReviewReasonCode.NONE
                        )
                )
        );

        assertEquals(WorkflowAction.RETRY_STAGE, flowDecision.action());
        assertEquals(StageType.IMPLEMENTATION, flowDecision.targetStage());
        assertTrue(flowDecision.revisionRoutingPlan().active());
        assertEquals(FixMode.PATCH, flowDecision.revisionRoutingPlan().fixMode());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                flowDecision.revisionRoutingPlan().implementationPatchTarget());
        assertEquals("src/game.js", flowDecision.revisionRoutingPlan().overrideChanges().getFirst().path());
    }

    @Test
    void blocksForHumanReviewWhenImplementationContinuationIsBlocked() {
        FlowController controller = new FlowController();
        ReviewResult reviewResult = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.REWORK,
                "代码未满足验收",
                "继续修复 implementation"
        );
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.RETRY_STAGE,
                StageType.IMPLEMENTATION,
                FixMode.REWORK,
                "回 implementation 修复",
                List.of(),
                List.of(),
                List.of(),
                DeliveryPolicy.patchSafe(),
                false
        );

        FlowDecision flowDecision = controller.decide(
                StageType.CODE_REVIEW,
                reviewResult,
                false,
                supervisorDecision,
                StageToolResultSummary.none(),
                new ImplementationRevisionFacts(
                        false,
                        true,
                        List.of("实现行消除与计分系统"),
                        new StageContinuationContext(
                                "当前实现需要继续 patch，但阶段汇总没有拿到结构化文件范围，不能自动续跑。",
                                "请先补齐 overrideChanges 指向的受影响文件。",
                                "evidence",
                                "actionItems",
                                List.of(),
                                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                                devflow.agent.review.ReviewReasonCode.IMPLEMENTATION_GAP
                        )
                )
        );

        assertEquals(WorkflowAction.REQUEST_HUMAN_REVIEW, flowDecision.action());
        assertEquals(StageType.CODE_REVIEW, flowDecision.targetStage());
        assertFalse(flowDecision.revisionRoutingPlan().active());
        assertEquals(TransitionReason.HUMAN_REVIEW_REQUIRED, flowDecision.transitionDecision().reason());
        assertEquals("当前实现需要继续 patch，但阶段汇总没有拿到结构化文件范围，不能自动续跑。",
                flowDecision.reviewResultOverride().summary());
        assertEquals("请先补齐 overrideChanges 指向的受影响文件。",
                flowDecision.reviewResultOverride().changeRequest());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                flowDecision.reviewResultOverride().implementationPatchTarget());
    }
}
