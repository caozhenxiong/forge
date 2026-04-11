package devflow.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ArtifactSummaryBuilder;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ProjectedContext;
import devflow.agent.executor.GenerationEngine;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StageReviewer;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.supervisor.SupervisorFallbackPolicy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageProgressCoordinatorTests {

    @TempDir
    Path tempDir;

    @Test
    void incompleteImplementationContinuesWithoutReviewerOrSupervisor() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        WorkflowArtifactRenderer workflowArtifactRenderer = new WorkflowArtifactRenderer();
        AtomicBoolean reviewerCalled = new AtomicBoolean(false);
        AtomicBoolean diagnosisCalled = new AtomicBoolean(false);
        AtomicBoolean supervisorCalled = new AtomicBoolean(false);
        AtomicBoolean contextProjected = new AtomicBoolean(false);
        AtomicBoolean applyCalled = new AtomicBoolean(false);
        AtomicReference<String> continuationSummary = new AtomicReference<>();
        AtomicReference<String> continuationChangeRequest = new AtomicReference<>();

        RunRecord runRecord = runningImplementationRun();
        runRepository.save(runRecord);
        artifactStore.writeArtifact(
                tempDir,
                runRecord.runId(),
                StageType.IMPLEMENTATION,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(false, false, true, java.util.List.of("补齐方块渲染"))
                )
        );

        StageOperationExecutor stageOperationExecutor = new StageOperationExecutor(
                null,
                reviewerThatSetsFlag(reviewerCalled),
                eventLogStore,
                new GenerationEngine(),
                new StageOperationPolicy()
        );
        DiagnosisAgent diagnosisAgent = diagnosisAgentThatSetsFlag(artifactStore, diagnosisCalled);
        SupervisorAgent supervisorAgent = supervisorAgentThatSetsFlag(artifactStore, supervisorCalled);
        ContextProjector contextProjector = new ContextProjector(
                artifactStore,
                new FileProjectWorkspace(),
                new ArtifactSummaryBuilder(),
                new ContractExtractor(),
                new devflow.agent.context.ContextLayerAssembler()
        ) {
            @Override
            public ProjectedContext project(Path projectPath, RunRecord currentRun, StageType currentStage) {
                contextProjected.set(true);
                throw new AssertionError("implementation continuation should not project context");
            }
        };
        FlowDecisionExecutor flowDecisionExecutor = new FlowDecisionExecutor(null, null) {
            @Override
            public RunRecord apply(
                    Path projectPath,
                    RunRecord currentRun,
                    StageType stageType,
                    ReviewResult reviewResult,
                    boolean repeatedIssue,
                    devflow.agent.supervisor.SupervisorDecision supervisorDecision,
                    FlowDecision flowDecision
            ) {
                applyCalled.set(true);
                throw new AssertionError("incomplete implementation should not route through review flow");
            }

            @Override
            public RunRecord continueStage(
                    Path projectPath,
                    RunRecord currentRun,
                    StageType stageType,
                    String summary,
                    String changeRequest,
                    String evidence,
                    String actionItems,
                    ImplementationPatchTarget implementationPatchTarget
            ) {
                continuationSummary.set(summary);
                continuationChangeRequest.set(changeRequest);
                return currentRun;
            }
        };
        StageProgressCoordinator coordinator = new StageProgressCoordinator(
                artifactStore,
                diagnosisAgent,
                supervisorAgent,
                new FlowController(),
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                new StageProgressArtifactSupport(artifactStore, eventLogStore, workflowArtifactRenderer),
                new StageToolResultLoader(artifactStore),
                new StageToolResultGuard()
        );

        var result = coordinator.progress(tempDir, runRecord);

        assertFalse(reviewerCalled.get());
        assertFalse(diagnosisCalled.get());
        assertFalse(supervisorCalled.get());
        assertFalse(contextProjected.get());
        assertFalse(applyCalled.get());
        assertTrue(continuationSummary.get().contains("阶段中间态"));
        assertTrue(continuationChangeRequest.get().contains("继续完成未完成的 implementation 子任务"));
        assertNotNull(result.transitionDecision());
        assertEquals(devflow.agent.loop.TransitionReason.STAGE_CONTINUE, result.transitionDecision().reason());
        assertEquals(StageType.IMPLEMENTATION, result.transitionDecision().targetStage());
        assertNull(result.transitionDecision().supervisorDecision());
    }

    @Test
    void blockedImplementationRequestsHumanWithoutContinuingStage() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        WorkflowArtifactRenderer workflowArtifactRenderer = new WorkflowArtifactRenderer();
        AtomicBoolean reviewerCalled = new AtomicBoolean(false);
        AtomicBoolean diagnosisCalled = new AtomicBoolean(false);
        AtomicBoolean supervisorCalled = new AtomicBoolean(false);
        AtomicBoolean contextProjected = new AtomicBoolean(false);
        AtomicBoolean continueCalled = new AtomicBoolean(false);
        AtomicBoolean blockedCalled = new AtomicBoolean(false);
        AtomicReference<ReviewResult> blockedReview = new AtomicReference<>();

        RunRecord runRecord = runningImplementationRun();
        runRepository.save(runRecord);
        artifactStore.writeArtifact(
                tempDir,
                runRecord.runId(),
                StageType.IMPLEMENTATION,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(
                                false,
                                false,
                                true,
                                "",
                                "",
                                "",
                                java.util.List.of("补齐方块渲染"),
                                ImplementationContinuationMode.BLOCK_STAGE,
                                "probe invalid",
                                "fix probe contract",
                                "unexpected field bodyTextLength",
                                "wait for human",
                                ImplementationPatchTarget.NONE,
                                ReviewReasonCode.RUNTIME_PROBE_INVALID
                        )
                )
        );

        StageOperationExecutor stageOperationExecutor = new StageOperationExecutor(
                null,
                reviewerThatSetsFlag(reviewerCalled),
                eventLogStore,
                new GenerationEngine(),
                new StageOperationPolicy()
        );
        DiagnosisAgent diagnosisAgent = diagnosisAgentThatSetsFlag(artifactStore, diagnosisCalled);
        SupervisorAgent supervisorAgent = supervisorAgentThatSetsFlag(artifactStore, supervisorCalled);
        ContextProjector contextProjector = new ContextProjector(
                artifactStore,
                new FileProjectWorkspace(),
                new ArtifactSummaryBuilder(),
                new ContractExtractor(),
                new devflow.agent.context.ContextLayerAssembler()
        ) {
            @Override
            public ProjectedContext project(Path projectPath, RunRecord currentRun, StageType currentStage) {
                contextProjected.set(true);
                throw new AssertionError("blocked implementation should not project context");
            }
        };
        FlowDecisionExecutor flowDecisionExecutor = new FlowDecisionExecutor(null, null) {
            @Override
            public RunRecord continueStage(
                    Path projectPath,
                    RunRecord currentRun,
                    StageType stageType,
                    String summary,
                    String changeRequest,
                    String evidence,
                    String actionItems,
                    ImplementationPatchTarget implementationPatchTarget
            ) {
                continueCalled.set(true);
                throw new AssertionError("blocked implementation should not continue stage");
            }

            @Override
            public RunRecord blockForHumanReview(
                    RunRecord currentRun,
                    StageType stageType,
                    ReviewResult reviewResult
            ) {
                blockedCalled.set(true);
                blockedReview.set(reviewResult);
                return currentRun.withCurrentStage(stageType, RunStatus.BLOCKED, currentRun.stageStates(), Instant.now());
            }
        };
        StageProgressCoordinator coordinator = new StageProgressCoordinator(
                artifactStore,
                diagnosisAgent,
                supervisorAgent,
                new FlowController(),
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                new StageProgressArtifactSupport(artifactStore, eventLogStore, workflowArtifactRenderer),
                new StageToolResultLoader(artifactStore),
                new StageToolResultGuard()
        );

        var result = coordinator.progress(tempDir, runRecord);

        assertFalse(reviewerCalled.get());
        assertFalse(diagnosisCalled.get());
        assertFalse(supervisorCalled.get());
        assertFalse(contextProjected.get());
        assertFalse(continueCalled.get());
        assertTrue(blockedCalled.get());
        assertEquals(ReviewReasonCode.RUNTIME_PROBE_INVALID, blockedReview.get().reasonCode());
        assertNotNull(result.transitionDecision());
        assertEquals(devflow.agent.loop.TransitionReason.HUMAN_REVIEW_REQUIRED, result.transitionDecision().reason());
        assertEquals(StageType.IMPLEMENTATION, result.transitionDecision().targetStage());
        assertNull(result.transitionDecision().supervisorDecision());
    }

    private StageReviewer reviewerThatSetsFlag(AtomicBoolean reviewerCalled) {
        LlmProvider provider = fakeProvider();
        return new StageReviewer(
                provider,
                new devflow.agent.project.WorkspaceSnapshotStore(new FileRunRepository(), new FileProjectWorkspace()),
                new devflow.agent.executor.TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()),
                new devflow.agent.prompt.PromptTemplateCatalog(),
                new devflow.agent.i18n.LanguagePolicy(),
                new devflow.agent.loop.AgentTurnLoop()
        ) {
            @Override
            public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
                reviewerCalled.set(true);
                throw new AssertionError("incomplete implementation should not enter reviewer");
            }
        };
    }

    private DiagnosisAgent diagnosisAgentThatSetsFlag(FileArtifactStore artifactStore, AtomicBoolean diagnosisCalled) {
        return new DiagnosisAgent(fakeProvider(), artifactStore, new ObjectMapper()) {
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
                throw new AssertionError("incomplete implementation should not invoke diagnosis");
            }
        };
    }

    private SupervisorAgent supervisorAgentThatSetsFlag(FileArtifactStore artifactStore, AtomicBoolean supervisorCalled) {
        ContextProjector projector = new ContextProjector(
                artifactStore,
                new FileProjectWorkspace(),
                new ArtifactSummaryBuilder(),
                new ContractExtractor(),
                new devflow.agent.context.ContextLayerAssembler()
        );
        return new SupervisorAgent(
                fakeProvider(),
                new ObjectMapper(),
                projector,
                new StageFlowPolicy(),
                new SupervisorFallbackPolicy(new StageFlowPolicy())
        ) {
            @Override
            public devflow.agent.supervisor.SupervisorDecision decide(
                    Path projectPath,
                    RunRecord runRecord,
                    StageType currentStage,
                    ReviewResult reviewResult,
                    boolean repeatedIssue
            ) {
                supervisorCalled.set(true);
                throw new AssertionError("incomplete implementation should not invoke supervisor");
            }
        };
    }

    private LlmProvider fakeProvider() {
        return new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
    }

    private RunRecord runningImplementationRun() {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            StageStatus status = stageType == StageType.IMPLEMENTATION ? StageStatus.RUNNING : StageStatus.PENDING;
            int attempt = stageType == StageType.IMPLEMENTATION ? 1 : 0;
            states.put(stageType, new StageExecution(stageType, status, attempt, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "constraints",
                new RunConfig(Map.of(), 5),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }
}
