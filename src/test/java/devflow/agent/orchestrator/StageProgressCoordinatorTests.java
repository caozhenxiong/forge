package devflow.agent.orchestrator;

import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ArtifactSummaryBuilder;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ProjectedContext;
import devflow.agent.context.TaskMemory;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StageReviewer;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.SupervisorDecision;
import devflow.agent.supervisor.SupervisorFallbackPolicy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
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
        AtomicReference<java.util.List<FileChange>> continuationOverrideChanges = new AtomicReference<>();

        RunRecord runRecord = runningImplementationRun();
        runRepository.save(runRecord);
        writeImplementationArtifacts(
                artifactStore,
                runRecord,
                new ImplementationStageStatusPayload(
                        false,
                        false,
                        java.util.List.of("补齐方块渲染"),
                        null,
                        ImplementationContinuationMode.CONTINUE_SUBTASKS,
                        "实现计划尚未执行完毕，当前仍处于阶段中间态。",
                        "请继续完成未完成的 implementation 子任务，补齐骨架后的真实行为实现，再重新进入 implementation review。",
                        "未完成子任务：补齐方块渲染",
                        "1. 继续执行未完成的实现子任务。 2. 补齐当前阶段计划中的缺失能力。 3. 仅在所有计划子任务完成后再提交 implementation 审阅。",
                        java.util.List.of(),
                        ImplementationPatchTarget.NONE,
                        ReviewReasonCode.NONE
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
                new devflow.agent.context.ContextProjectionArtifactReader(artifactStore, new FileProjectWorkspace()),
                new devflow.agent.context.ContextProjectionContractResolver(
                        new ContractExtractor(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                new devflow.agent.context.ContextProjectionSummaryAssembler(new ArtifactSummaryBuilder()),
                new devflow.agent.context.ContextProjectionAssembler(new devflow.agent.context.ContextLayerAssembler())
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
                    StageContinuationContext continuationContext
            ) {
                continuationSummary.set(continuationContext.summary());
                continuationChangeRequest.set(continuationContext.changeRequest());
                continuationOverrideChanges.set(continuationContext.overrideChanges());
                return currentRun;
            }
        };
        StageProgressCoordinator coordinator = new StageProgressCoordinator(
                artifactStore,
                supervisorAgent,
                new FlowController(),
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                new StageProgressArtifactSupport(artifactStore, eventLogStore, workflowArtifactRenderer),
                new StageToolResultGate(new StageToolResultLoader(artifactStore), new StageToolResultGuard()),
                new ImplementationProgressSupport(
                        artifactStore,
                        new devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport(),
                        new ImplementationContinuationSupport()
                ),
                new RepeatIssueDetector(diagnosisAgent),
                new devflow.agent.i18n.LanguagePolicy()
        );

        var result = coordinator.progress(tempDir, runRecord);

        assertFalse(reviewerCalled.get());
        assertFalse(diagnosisCalled.get());
        assertFalse(supervisorCalled.get());
        assertFalse(contextProjected.get());
        assertFalse(applyCalled.get());
        assertEquals("实现计划尚未执行完毕，当前仍处于阶段中间态。", continuationSummary.get());
        assertEquals("请继续完成未完成的 implementation 子任务，补齐骨架后的真实行为实现，再重新进入 implementation review。", continuationChangeRequest.get());
        assertTrue(continuationOverrideChanges.get().isEmpty());
        assertNotNull(result.transitionDecision());
        assertEquals(devflow.agent.loop.TransitionReason.STAGE_CONTINUE, result.transitionDecision().reason());
        assertEquals(StageType.IMPLEMENTATION, result.transitionDecision().targetStage());
        assertNull(result.transitionDecision().supervisorDecision());
    }

    @Test
    void incompleteImplementationForwardsStructuredContinuationPatch() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        WorkflowArtifactRenderer workflowArtifactRenderer = new WorkflowArtifactRenderer();
        AtomicBoolean reviewerCalled = new AtomicBoolean(false);
        AtomicBoolean diagnosisCalled = new AtomicBoolean(false);
        AtomicBoolean supervisorCalled = new AtomicBoolean(false);
        AtomicBoolean contextProjected = new AtomicBoolean(false);
        AtomicReference<String> continuationSummary = new AtomicReference<>();
        AtomicReference<String> continuationChangeRequest = new AtomicReference<>();
        AtomicReference<String> continuationEvidence = new AtomicReference<>();
        AtomicReference<String> continuationActionItems = new AtomicReference<>();
        AtomicReference<java.util.List<FileChange>> continuationOverrideChanges = new AtomicReference<>();
        AtomicReference<ImplementationPatchTarget> continuationPatchTarget = new AtomicReference<>();

        RunRecord runRecord = runningImplementationRun();
        runRepository.save(runRecord);
        writeImplementationArtifacts(
                artifactStore,
                runRecord,
                new ImplementationStageStatusPayload(
                        false,
                        false,
                        java.util.List.of("补齐接线"),
                        null,
                        ImplementationContinuationMode.CONTINUE_SUBTASKS,
                        "继续修当前入口接线",
                        "只修宿主 HTML 与 companion runtime 的接线。",
                        "continuationSubtask=修接线\nindex.app.js exists but index.html does not reference it",
                        "1. 引入 companion runtime。 2. 不要重做业务逻辑。",
                        java.util.List.of(
                                new devflow.agent.protocol.FileChangePayload(
                                        "index.html",
                                        "WRITE",
                                        "修复宿主接线",
                                        "HOST_HTML_PATCH",
                                        "EXTERNAL_COMPANION",
                                        true
                                ),
                                new devflow.agent.protocol.FileChangePayload(
                                        "index.app.js",
                                        "WRITE",
                                        "对齐 companion runtime",
                                        "AUTO",
                                        null,
                                        false
                                )
                        ),
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        ReviewReasonCode.NONE
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
                new devflow.agent.context.ContextProjectionArtifactReader(artifactStore, new FileProjectWorkspace()),
                new devflow.agent.context.ContextProjectionContractResolver(
                        new ContractExtractor(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                new devflow.agent.context.ContextProjectionSummaryAssembler(new ArtifactSummaryBuilder()),
                new devflow.agent.context.ContextProjectionAssembler(new devflow.agent.context.ContextLayerAssembler())
        ) {
            @Override
            public ProjectedContext project(Path projectPath, RunRecord currentRun, StageType currentStage) {
                contextProjected.set(true);
                throw new AssertionError("structured implementation continuation should not project context");
            }
        };
        FlowDecisionExecutor flowDecisionExecutor = new FlowDecisionExecutor(null, null) {
            @Override
            public RunRecord continueStage(
                    Path projectPath,
                    RunRecord currentRun,
                    StageType stageType,
                    StageContinuationContext continuationContext
            ) {
                continuationSummary.set(continuationContext.summary());
                continuationChangeRequest.set(continuationContext.changeRequest());
                continuationEvidence.set(continuationContext.evidence());
                continuationActionItems.set(continuationContext.actionItems());
                continuationOverrideChanges.set(continuationContext.overrideChanges());
                continuationPatchTarget.set(continuationContext.implementationPatchTarget());
                return currentRun;
            }
        };
        StageProgressCoordinator coordinator = new StageProgressCoordinator(
                artifactStore,
                supervisorAgent,
                new FlowController(),
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                new StageProgressArtifactSupport(artifactStore, eventLogStore, workflowArtifactRenderer),
                new StageToolResultGate(new StageToolResultLoader(artifactStore), new StageToolResultGuard()),
                new ImplementationProgressSupport(
                        artifactStore,
                        new devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport(),
                        new ImplementationContinuationSupport()
                ),
                new RepeatIssueDetector(diagnosisAgent),
                new devflow.agent.i18n.LanguagePolicy()
        );

        var result = coordinator.progress(tempDir, runRecord);

        assertFalse(reviewerCalled.get());
        assertFalse(diagnosisCalled.get());
        assertFalse(supervisorCalled.get());
        assertFalse(contextProjected.get());
        assertEquals("继续修当前入口接线", continuationSummary.get());
        assertEquals("只修宿主 HTML 与 companion runtime 的接线。", continuationChangeRequest.get());
        assertTrue(continuationEvidence.get().contains("continuationSubtask=修接线"));
        assertTrue(continuationActionItems.get().contains("引入 companion runtime"));
        assertEquals(2, continuationOverrideChanges.get().size());
        assertEquals("index.html", continuationOverrideChanges.get().getFirst().path());
        assertEquals(ChangeAction.WRITE, continuationOverrideChanges.get().getFirst().action());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, continuationPatchTarget.get());
        assertNotNull(result.transitionDecision());
        assertEquals(devflow.agent.loop.TransitionReason.STAGE_CONTINUE, result.transitionDecision().reason());
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
        writeImplementationArtifacts(
                artifactStore,
                runRecord,
                new ImplementationStageStatusPayload(
                        false,
                        false,
                        java.util.List.of("补齐方块渲染"),
                        null,
                        ImplementationContinuationMode.BLOCK_STAGE,
                        "probe invalid",
                        "fix probe contract",
                        "unexpected field bodyTextLength",
                        "wait for human",
                        java.util.List.of(),
                        ImplementationPatchTarget.NONE,
                        ReviewReasonCode.RUNTIME_PROBE_INVALID
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
                new devflow.agent.context.ContextProjectionArtifactReader(artifactStore, new FileProjectWorkspace()),
                new devflow.agent.context.ContextProjectionContractResolver(
                        new ContractExtractor(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                new devflow.agent.context.ContextProjectionSummaryAssembler(new ArtifactSummaryBuilder()),
                new devflow.agent.context.ContextProjectionAssembler(new devflow.agent.context.ContextLayerAssembler())
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
                    StageContinuationContext continuationContext
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
                supervisorAgent,
                new FlowController(),
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                new StageProgressArtifactSupport(artifactStore, eventLogStore, workflowArtifactRenderer),
                new StageToolResultGate(new StageToolResultLoader(artifactStore), new StageToolResultGuard()),
                new ImplementationProgressSupport(
                        artifactStore,
                        new devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport(),
                        new ImplementationContinuationSupport()
                ),
                new RepeatIssueDetector(diagnosisAgent),
                new devflow.agent.i18n.LanguagePolicy()
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

    @Test
    void stageProgressCoordinatorDoesNotDeriveReadyFromPlanCompletedState() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        WorkflowArtifactRenderer workflowArtifactRenderer = new WorkflowArtifactRenderer();
        AtomicBoolean reviewerCalled = new AtomicBoolean(false);
        AtomicReference<String> continuationSummary = new AtomicReference<>();

        RunRecord runRecord = runningImplementationRun();
        runRepository.save(runRecord);
        writeImplementationArtifacts(
                artifactStore,
                runRecord,
                new ImplementationStageStatusPayload(
                        false,
                        true,
                        java.util.List.of(),
                        new ImplementationStageStatusPayload.ContractGatePayload(
                                "STAGE_COMPLETION",
                                true,
                                "",
                                "",
                                ImplementationPatchTarget.NONE.name(),
                                null
                        ),
                        ImplementationContinuationMode.CONTINUE_SUBTASKS,
                        "继续修当前子任务",
                        "只按当前 implementation_state 的续跑要求继续。",
                        "deterministic continuation payload",
                        "1. 继续修复。 2. 再验证。",
                        java.util.List.of(),
                        ImplementationPatchTarget.NONE,
                        ReviewReasonCode.NONE
                )
        );

        FlowDecisionExecutor flowDecisionExecutor = new FlowDecisionExecutor(null, null) {
            @Override
            public RunRecord continueStage(
                    Path projectPath,
                    RunRecord currentRun,
                    StageType stageType,
                    StageContinuationContext continuationContext
            ) {
                continuationSummary.set(continuationContext.summary());
                return currentRun;
            }
        };
        StageProgressCoordinator coordinator = new StageProgressCoordinator(
                artifactStore,
                supervisorAgentThatSetsFlag(artifactStore, new AtomicBoolean(false)),
                new FlowController(),
                new ContextProjector(
                        new devflow.agent.context.ContextProjectionArtifactReader(artifactStore, new FileProjectWorkspace()),
                        new devflow.agent.context.ContextProjectionContractResolver(
                                new ContractExtractor(),
                                new devflow.agent.i18n.LanguagePolicy()
                        ),
                        new devflow.agent.context.ContextProjectionSummaryAssembler(new ArtifactSummaryBuilder()),
                        new devflow.agent.context.ContextProjectionAssembler(new devflow.agent.context.ContextLayerAssembler())
                ),
                new StageOperationExecutor(
                        null,
                        reviewerThatSetsFlag(reviewerCalled),
                        eventLogStore,
                        new GenerationEngine(),
                        new StageOperationPolicy()
                ),
                flowDecisionExecutor,
                new StageProgressArtifactSupport(artifactStore, eventLogStore, workflowArtifactRenderer),
                new StageToolResultGate(new StageToolResultLoader(artifactStore), new StageToolResultGuard()),
                new ImplementationProgressSupport(
                        artifactStore,
                        new devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport(),
                        new ImplementationContinuationSupport()
                ),
                new RepeatIssueDetector(diagnosisAgentThatSetsFlag(artifactStore, new AtomicBoolean(false))),
                new devflow.agent.i18n.LanguagePolicy()
        );

        coordinator.progress(tempDir, runRecord);

        assertFalse(reviewerCalled.get());
        assertEquals("继续修当前子任务", continuationSummary.get());
    }

    @Test
    void blockedImplementationReviewOverrideStaysAlignedAcrossTransitionArtifactAndRunState() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        WorkflowArtifactRenderer workflowArtifactRenderer = new WorkflowArtifactRenderer();
        AtomicBoolean reviewerCalled = new AtomicBoolean(false);
        AtomicBoolean diagnosisCalled = new AtomicBoolean(false);
        AtomicBoolean supervisorCalled = new AtomicBoolean(false);
        AtomicBoolean contextProjected = new AtomicBoolean(false);

        String blockedSummary = "当前 implementation continuation 缺少可自动续跑的结构化 patch scope。";
        String blockedChangeRequest = "请人工确认 host entry 与 runtime companion 的最终修复范围。";
        String rawReviewChangeRequest = "原始 code review 只给出泛化返工意见。";

        RunRecord runRecord = runningCodeReviewRun();
        runRepository.save(runRecord);
        artifactStore.writeArtifact(
                tempDir,
                runRecord.runId(),
                StageType.CODE_REVIEW,
                "# code review\n"
        );
        writeImplementationArtifacts(
                artifactStore,
                runRecord,
                new ImplementationStageStatusPayload(
                        false,
                        false,
                        List.of("修复宿主入口接线"),
                        null,
                        ImplementationContinuationMode.BLOCK_STAGE,
                        blockedSummary,
                        blockedChangeRequest,
                        "index.app.js exists but host entry wiring scope is unresolved",
                        "1. 明确 host entry。 2. 确认允许修复的文件范围。",
                        List.of(
                                new devflow.agent.protocol.FileChangePayload(
                                        "index.html",
                                        "WRITE",
                                        "修复 host entry 接线",
                                        "HOST_HTML_PATCH",
                                        "EXTERNAL_COMPANION",
                                        true
                                )
                        ),
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        ReviewReasonCode.RUNTIME_PROBE_INVALID
                )
        );

        StageOperationExecutor stageOperationExecutor = new StageOperationExecutor(
                null,
                reviewerReturning(
                        reviewerCalled,
                        new ReviewResult(
                                ReviewDecision.REVISION_REQUIRED,
                                FixMode.REWORK,
                                "",
                                rawReviewChangeRequest,
                                "review evidence",
                                "review action items"
                        )
                ),
                eventLogStore,
                new GenerationEngine(),
                new StageOperationPolicy()
        );
        DiagnosisAgent diagnosisAgent = diagnosisAgentReturningFalse(diagnosisCalled);
        ContextProjector contextProjector = new ContextProjector(
                new devflow.agent.context.ContextProjectionArtifactReader(artifactStore, new FileProjectWorkspace()),
                new devflow.agent.context.ContextProjectionContractResolver(
                        new ContractExtractor(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                new devflow.agent.context.ContextProjectionSummaryAssembler(new ArtifactSummaryBuilder()),
                new devflow.agent.context.ContextProjectionAssembler(new devflow.agent.context.ContextLayerAssembler())
        ) {
            @Override
            public ProjectedContext project(Path projectPath, RunRecord currentRun, StageType currentStage) {
                contextProjected.set(true);
                return projectedContext();
            }
        };
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                devflow.agent.domain.WorkflowAction.RETRY_STAGE,
                StageType.IMPLEMENTATION,
                FixMode.REWORK,
                "回 implementation 修复",
                List.of("聚焦 runtime wiring"),
                List.of("不要扩张到未批准文件"),
                List.of("给出结构化 patch scope"),
                DeliveryPolicy.patchSafe(),
                false
        );
        SupervisorAgent supervisorAgent = supervisorAgentReturning(artifactStore, supervisorCalled, supervisorDecision);
        StageStatusSupport stageStatusSupport = new StageStatusSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                new StageFlowPolicy(),
                workflowArtifactRenderer
        );
        FlowDecisionExecutor flowDecisionExecutor = new FlowDecisionExecutor(
                new StageTransitionSupport(stageStatusSupport, null, null),
                null
        );
        StageProgressCoordinator coordinator = new StageProgressCoordinator(
                artifactStore,
                supervisorAgent,
                new FlowController(),
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                new StageProgressArtifactSupport(artifactStore, eventLogStore, workflowArtifactRenderer),
                new StageToolResultGate(new StageToolResultLoader(artifactStore), new StageToolResultGuard()),
                new ImplementationProgressSupport(
                        artifactStore,
                        new devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport(),
                        new ImplementationContinuationSupport()
                ),
                new RepeatIssueDetector(diagnosisAgent),
                new devflow.agent.i18n.LanguagePolicy()
        );

        var result = coordinator.progress(tempDir, runRecord);

        assertTrue(reviewerCalled.get());
        assertTrue(diagnosisCalled.get());
        assertTrue(supervisorCalled.get());
        assertTrue(contextProjected.get());
        assertNotNull(result.transitionDecision());
        assertEquals(devflow.agent.loop.TransitionReason.HUMAN_REVIEW_REQUIRED, result.transitionDecision().reason());
        assertEquals(StageType.CODE_REVIEW, result.transitionDecision().targetStage());
        assertEquals(blockedSummary, result.transitionDecision().summary());
        assertFalse(result.continueLoop());

        String transitionArtifact = artifactStore.readAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.TRANSITION_DECISION
        );
        assertTrue(transitionArtifact.contains("reason: HUMAN_REVIEW_REQUIRED"));
        assertTrue(transitionArtifact.contains("targetStage: CODE_REVIEW"));
        assertTrue(transitionArtifact.contains("reviewSummary: " + blockedSummary));
        assertTrue(transitionArtifact.contains("supervisorAction: RETRY_STAGE"));

        RunRecord reloaded = runRepository.findById(tempDir, runRecord.runId()).orElseThrow();
        StageExecution codeReviewExecution = reloaded.stageStates().get(StageType.CODE_REVIEW);
        assertEquals(RunStatus.BLOCKED, reloaded.status());
        assertEquals(StageType.CODE_REVIEW, reloaded.currentStage());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, codeReviewExecution.status());
        assertEquals(ReviewDecision.REVISION_REQUIRED, codeReviewExecution.reviewDecision());
        assertEquals(blockedSummary, codeReviewExecution.reviewSummary());
        assertEquals(blockedChangeRequest, codeReviewExecution.changeRequest());
        assertFalse(rawReviewChangeRequest.equals(codeReviewExecution.changeRequest()));
    }

    private StageReviewer reviewerThatSetsFlag(AtomicBoolean reviewerCalled) {
        LlmProvider provider = fakeProvider();
        return new devflow.agent.review.StageReviewerHarness(
                provider,
                new devflow.agent.project.WorkspaceSnapshotStore(new FileRunRepository(), new FileProjectWorkspace()),
                devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), provider, new ObjectMapper())
        ) {
            @Override
            public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
                reviewerCalled.set(true);
                throw new AssertionError("incomplete implementation should not enter reviewer");
            }
        };
    }

    private StageReviewer reviewerReturning(AtomicBoolean reviewerCalled, ReviewResult reviewResult) {
        return new StageReviewer(null, null, null) {
            @Override
            public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
                reviewerCalled.set(true);
                return reviewResult;
            }

            @Override
            public GenerationTelemetry consumeLastTelemetry() {
                return null;
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

    private DiagnosisAgent diagnosisAgentReturningFalse(AtomicBoolean diagnosisCalled) {
        return new DiagnosisAgent(fakeProvider(), new FileArtifactStore(new FileRunRepository()), new ObjectMapper()) {
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
                return false;
            }
        };
    }

    private SupervisorAgent supervisorAgentThatSetsFlag(FileArtifactStore artifactStore, AtomicBoolean supervisorCalled) {
        ContextProjector projector = new ContextProjector(
                new devflow.agent.context.ContextProjectionArtifactReader(artifactStore, new FileProjectWorkspace()),
                new devflow.agent.context.ContextProjectionContractResolver(
                        new ContractExtractor(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                new devflow.agent.context.ContextProjectionSummaryAssembler(new ArtifactSummaryBuilder()),
                new devflow.agent.context.ContextProjectionAssembler(new devflow.agent.context.ContextLayerAssembler())
        );
        return new SupervisorAgent(
                fakeProvider(),
                projector,
                new StageFlowPolicy(),
                devflow.agent.supervisor.SupervisorTestSupport.newFallbackPolicy(new StageFlowPolicy()),
                new devflow.agent.supervisor.SupervisorArtifactRenderer(),
                new devflow.agent.supervisor.SupervisorDecisionSanitizer(
                        new StageFlowPolicy(),
                        new devflow.agent.supervisor.SupervisorPayloadNormalizer(),
                        devflow.agent.supervisor.SupervisorTestSupport.newFallbackPolicy(new StageFlowPolicy())
                ),
                new devflow.agent.supervisor.SupervisorPromptAssembler(new devflow.agent.supervisor.SupervisorArtifactRenderer()),
                new devflow.agent.executor.llm.StructuredPayloadReader(new ObjectMapper()),
                new devflow.agent.i18n.LanguagePolicy()
        ) {
            @Override
            public devflow.agent.supervisor.SupervisorDecision decide(
                    RunRecord runRecord,
                    StageType currentStage,
                    ReviewResult reviewResult,
                    boolean repeatedIssue,
                    ProjectedContext projectedContext
            ) {
                supervisorCalled.set(true);
                throw new AssertionError("incomplete implementation should not invoke supervisor");
            }
        };
    }

    private SupervisorAgent supervisorAgentReturning(
            FileArtifactStore artifactStore,
            AtomicBoolean supervisorCalled,
            SupervisorDecision supervisorDecision
    ) {
        ContextProjector projector = new ContextProjector(
                new devflow.agent.context.ContextProjectionArtifactReader(artifactStore, new FileProjectWorkspace()),
                new devflow.agent.context.ContextProjectionContractResolver(
                        new ContractExtractor(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                new devflow.agent.context.ContextProjectionSummaryAssembler(new ArtifactSummaryBuilder()),
                new devflow.agent.context.ContextProjectionAssembler(new devflow.agent.context.ContextLayerAssembler())
        );
        return new SupervisorAgent(
                fakeProvider(),
                projector,
                new StageFlowPolicy(),
                devflow.agent.supervisor.SupervisorTestSupport.newFallbackPolicy(new StageFlowPolicy()),
                new devflow.agent.supervisor.SupervisorArtifactRenderer(),
                new devflow.agent.supervisor.SupervisorDecisionSanitizer(
                        new StageFlowPolicy(),
                        new devflow.agent.supervisor.SupervisorPayloadNormalizer(),
                        devflow.agent.supervisor.SupervisorTestSupport.newFallbackPolicy(new StageFlowPolicy())
                ),
                new devflow.agent.supervisor.SupervisorPromptAssembler(new devflow.agent.supervisor.SupervisorArtifactRenderer()),
                new devflow.agent.executor.llm.StructuredPayloadReader(new ObjectMapper()),
                new devflow.agent.i18n.LanguagePolicy()
        ) {
            @Override
            public devflow.agent.supervisor.SupervisorDecision decide(
                    RunRecord runRecord,
                    StageType currentStage,
                    ReviewResult reviewResult,
                    boolean repeatedIssue,
                    ProjectedContext projectedContext
            ) {
                supervisorCalled.set(true);
                return supervisorDecision;
            }
        };
    }

    private LlmProvider fakeProvider() {
        return new devflow.agent.testsupport.RequestBackedLlmProvider() {
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

    private void writeImplementationArtifacts(
            FileArtifactStore artifactStore,
            RunRecord runRecord,
            ImplementationStageStatusPayload payload
    ) {
        artifactStore.writeArtifact(
                tempDir,
                runRecord.runId(),
                StageType.IMPLEMENTATION,
                "# implementation\n"
        );
        artifactStore.writeAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.IMPLEMENTATION_STATE,
                renderImplementationState(payload)
        );
    }

    private String renderImplementationState(ImplementationStageStatusPayload payload) {
        try {
            java.util.LinkedHashMap<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("summary", "");
            root.put("subtasks", java.util.List.of());
            root.put("reports", java.util.List.of());
            root.put("events", java.util.List.of());
            root.put("currentSubtaskTitle", "");
            root.put("planCompleted", payload.planCompleted());
            root.put("stageReady", payload.stageReady());
            root.put("contractGate", renderContractGate(payload));
            root.put("continuationMode", payload.continuationMode() == null ? ImplementationContinuationMode.CONTINUE_SUBTASKS.name() : payload.continuationMode().name());
            root.put("continuationSummary", payload.continuationSummary());
            root.put("continuationChangeRequest", payload.continuationChangeRequest());
            root.put("continuationEvidence", payload.continuationEvidence());
            root.put("continuationActionItems", payload.continuationActionItems());
            root.put("continuationOverrideChanges", payload.continuationOverrideChanges() == null
                    ? java.util.List.of()
                    : payload.continuationOverrideChanges().stream().map(change -> {
                        java.util.LinkedHashMap<String, Object> fileChange = new java.util.LinkedHashMap<>();
                        fileChange.put("path", change.path());
                        fileChange.put("action", change.action());
                        fileChange.put("reason", change.reason());
                        fileChange.put("editScope", change.editScope());
                        fileChange.put("runtimeOwnership", change.runtimeOwnership());
                        fileChange.put("hostHtmlPatchRequired", change.hostHtmlPatchRequired());
                        return fileChange;
                    }).toList());
            root.put("continuationPatchTarget", payload.continuationPatchTarget() == null ? ImplementationPatchTarget.NONE.name() : payload.continuationPatchTarget().name());
            root.put("continuationReasonCode", payload.continuationReasonCode() == null ? ReviewReasonCode.NONE.name() : payload.continuationReasonCode().name());
            root.put("incompleteSubtasks", payload.incompleteSubtasks() == null ? java.util.List.of() : payload.incompleteSubtasks());
            return new ObjectMapper().writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, Object> renderContractGate(ImplementationStageStatusPayload payload) {
        if (payload.contractGate() == null) {
            return null;
        }
        java.util.LinkedHashMap<String, Object> contractGate = new java.util.LinkedHashMap<>();
        contractGate.put("scope", payload.contractGate().scope());
        contractGate.put("passed", payload.contractGate().passed());
        contractGate.put("failureReason", payload.contractGate().failureReason());
        contractGate.put("details", payload.contractGate().details());
        contractGate.put("implementationPatchTarget", payload.contractGate().patchTarget());
        contractGate.put("runtimeContract", renderRuntimeContract(payload));
        return contractGate;
    }

    private Map<String, Object> renderRuntimeContract(ImplementationStageStatusPayload payload) {
        if (payload.contractGate() == null || payload.contractGate().runtimeContract() == null) {
            return null;
        }
        java.util.LinkedHashMap<String, Object> runtimeContract = new java.util.LinkedHashMap<>();
        runtimeContract.put("htmlEntryPath", payload.contractGate().runtimeContract().htmlEntryPath());
        runtimeContract.put("runtimeOwnership", payload.contractGate().runtimeContract().runtimeOwnership());
        runtimeContract.put("runtimePaths", payload.contractGate().runtimeContract().runtimePaths());
        return runtimeContract;
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

    private RunRecord runningCodeReviewRun() {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            StageStatus status;
            int attempt;
            if (stageType.ordinal() < StageType.CODE_REVIEW.ordinal()) {
                status = StageStatus.APPROVED;
                attempt = 1;
            } else if (stageType == StageType.CODE_REVIEW) {
                status = StageStatus.RUNNING;
                attempt = 1;
            } else {
                status = StageStatus.PENDING;
                attempt = 0;
            }
            states.put(stageType, new StageExecution(stageType, status, attempt, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "constraints",
                new RunConfig(Map.of(), 5),
                StageType.CODE_REVIEW,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }

    private ProjectedContext projectedContext() {
        return new ProjectedContext(
                "当前阶段摘要",
                "上游契约摘要",
                "权威需求目录",
                "最近历史",
                "失败摘要",
                "修复摘要",
                "工作集摘要",
                new TaskMemory(
                        "目标",
                        "约束",
                        "当前阶段摘要",
                        "上游契约摘要",
                        "权威需求目录",
                        "最近历史",
                        "失败摘要",
                        "修复摘要",
                        "工作集摘要",
                        List.of()
                )
        );
    }
}
