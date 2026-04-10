package devflow.agent.supervisor;

import devflow.agent.context.ProjectedContext;
import devflow.agent.executor.GenerationFailureReport;
import devflow.agent.executor.GenerationFailureType;
import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageFlowPolicy;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SupervisorFallbackPolicyTests {

    @TempDir
    Path tempDir;

    private final SupervisorFallbackPolicy fallbackPolicy =
            new SupervisorFallbackPolicy(new StageFlowPolicy());

    @Test
    void requestsHumanReviewWhenApprovedStageNeedsHumanGate() {
        SupervisorDecision decision = fallbackPolicy.decideStageFallback(
                runRecord(StageType.ANALYSIS, GatePolicy.AGENT_PLUS_HUMAN),
                StageType.ANALYSIS,
                GatePolicy.AGENT_PLUS_HUMAN,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", ""),
                false,
                projectedContext()
        );

        assertEquals(SupervisorAction.REQUEST_HUMAN_REVIEW, decision.action());
        assertEquals(StageType.ANALYSIS, decision.targetStage());
    }

    @Test
    void routesRepeatedRuntimeIssueToRepair() {
        SupervisorDecision decision = fallbackPolicy.decideStageFallback(
                runRecord(StageType.CODE_REVIEW, GatePolicy.AGENT_ONLY),
                StageType.CODE_REVIEW,
                GatePolicy.AGENT_ONLY,
                new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "仍有相同问题", "继续修复"),
                true,
                projectedContext()
        );

        assertEquals(SupervisorAction.ROUTE_TO_REPAIR, decision.action());
        assertEquals(StageType.IMPLEMENTATION, decision.targetStage());
    }

    @Test
    void keepsPreciseEditingWhenRetryingPreciseFailure() {
        GenerationFailureReport report = new GenerationFailureReport(
                "game.js",
                "PATCH",
                "precise-code",
                GenerationFailureType.OUTPUT_TRUNCATED,
                3,
                true,
                "输出被截断",
                "done_reason=length",
                "请收缩改单范围"
        );

        GenerationRecoveryDecision decision = fallbackPolicy.decideGenerationFallback(
                report,
                1,
                DeliveryPolicy.patchSafe()
        );

        assertEquals(GenerationRecoveryAction.RETRY_SUBTASK, decision.action());
        Assertions.assertTrue(decision.deliveryPolicy().preferPreciseEditing());
    }

    private ProjectedContext projectedContext() {
        return new ProjectedContext("", "", "", "", "", "", "", null);
    }

    private RunRecord runRecord(StageType currentStage, GatePolicy gatePolicy) {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        stageStates.put(currentStage, new StageExecution(currentStage, StageStatus.RUNNING, 1, null, null, null, null));
        Map<StageType, GatePolicy> policies = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            policies.put(stageType, GatePolicy.AGENT_ONLY);
        }
        policies.put(currentStage, gatePolicy);
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览",
                new RunConfig(policies, 5),
                currentStage,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }
}
