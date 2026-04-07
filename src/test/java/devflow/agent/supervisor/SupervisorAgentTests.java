package devflow.agent.supervisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ArtifactSummaryBuilder;
import devflow.agent.context.ContextProjector;
import devflow.agent.executor.LlmProvider;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupervisorAgentTests {

    @TempDir
    Path tempDir;

    @Test
    void fallbackRequestsHumanReviewForApprovedDocumentStage() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        SupervisorAgent supervisorAgent = new SupervisorAgent(
                null,
                artifactStore,
                new ObjectMapper(),
                new ContextProjector(artifactStore, new FileProjectWorkspace(), new ArtifactSummaryBuilder())
        );

        RunRecord runRecord = runRecord(StageType.ANALYSIS, GatePolicy.AGENT_PLUS_HUMAN);
        SupervisorDecision decision = supervisorAgent.decide(
                tempDir,
                runRecord,
                StageType.ANALYSIS,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", ""),
                false
        );

        assertEquals(SupervisorAction.REQUEST_HUMAN_REVIEW, decision.action());
        assertEquals(StageType.ANALYSIS, decision.targetStage());
    }

    @Test
    void fallbackRoutesToRepairWhenRepeatedImplementationIssueDetected() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        SupervisorAgent supervisorAgent = new SupervisorAgent(
                null,
                artifactStore,
                new ObjectMapper(),
                new ContextProjector(artifactStore, new FileProjectWorkspace(), new ArtifactSummaryBuilder())
        );

        RunRecord runRecord = runRecord(StageType.CODE_REVIEW, GatePolicy.AGENT_PLUS_HUMAN);
        SupervisorDecision decision = supervisorAgent.decide(
                tempDir,
                runRecord,
                StageType.CODE_REVIEW,
                new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "仍有相同问题", "继续修复"),
                true
        );

        assertEquals(SupervisorAction.ROUTE_TO_REPAIR, decision.action());
        assertEquals(StageType.IMPLEMENTATION, decision.targetStage());
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
                "测试目标",
                "",
                new RunConfig(policies, 5),
                currentStage,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }
}
