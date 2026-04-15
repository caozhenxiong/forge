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
                        ImplementationContinuationMode.MID_PLAN_CONTINUE,
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
        assertEquals(devflow.agent.loop.TransitionReason.IMPLEMENTATION_MID_PLAN_CONTINUE, result.transitionDecision().reason());
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
                        ImplementationContinuationMode.PATCH_CONTINUE,
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
        assertEquals(devflow.agent.loop.TransitionReason.IMPLEMENTATION_PATCH_CONTINUE, result.transitionDecision().reason());
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
                        ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
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
        assertEquals(devflow.agent.loop.TransitionReason.IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK, result.transitionDecision().reason());
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
                        ImplementationContinuationMode.MID_PLAN_CONTINUE,
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
                        ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
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
        assertEquals(devflow.agent.loop.TransitionReason.IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK, result.transitionDecision().reason());
        assertEquals(StageType.CODE_REVIEW, result.transitionDecision().targetStage());
        assertEquals(blockedSummary, result.transitionDecision().summary());
        assertFalse(result.continueLoop());

        String transitionArtifact = artifactStore.readAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.TRANSITION_DECISION
        );
        assertTrue(transitionArtifact.contains("reason: IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK"));
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

    @Test
    void repairRouteStaysAlignedAcrossTransitionArtifactEventsAndRunState() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        WorkflowArtifactRenderer workflowArtifactRenderer = new WorkflowArtifactRenderer();
        AtomicBoolean reviewerCalled = new AtomicBoolean(false);
        AtomicBoolean diagnosisCalled = new AtomicBoolean(false);
        AtomicBoolean supervisorCalled = new AtomicBoolean(false);
        AtomicBoolean contextProjected = new AtomicBoolean(false);

        RunRecord runRecord = runningCodeReviewRun();
        runRepository.save(runRecord);
        artifactStore.writeArtifact(
                tempDir,
                runRecord.runId(),
                StageType.CODE_REVIEW,
                "# code review\n"
        );
        ReviewResult reviewResult = new ReviewResult(
                ReviewDecision.REJECTED,
                FixMode.PATCH,
                "当前实现缺少可验证运行证据",
                "回 implementation 修复当前缺口",
                "测试与代码评审均指出当前实现未闭环",
                "1. 只修当前批准范围。 2. 修完重新验证。",
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                List.of(new FileChange("index.html", ChangeAction.WRITE, "修复当前入口实现"))
        );
        StageOperationExecutor stageOperationExecutor = new StageOperationExecutor(
                null,
                reviewerReturning(reviewerCalled, reviewResult),
                eventLogStore,
                new GenerationEngine(),
                new StageOperationPolicy()
        ) {
            @Override
            public String composeStageArtifact(
                    Path projectPath,
                    RunRecord currentRun,
                    StageType stageType,
                    StageExecution stageExecution,
                    String note
            ) {
                return "# Repair Implementation\n\n补齐当前批准范围内的实现缺口。";
            }
        };
        DiagnosisAgent repeatIssueDiagnosisAgent = diagnosisAgentReturningFalse(diagnosisCalled);
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
                devflow.agent.domain.WorkflowAction.ROUTE_TO_REPAIR,
                StageType.IMPLEMENTATION,
                FixMode.REWORK,
                "进入 repair 路由",
                List.of("聚焦当前实现缺口"),
                List.of("不要扩张到未批准文件"),
                List.of("补齐运行证据"),
                DeliveryPolicy.patchSafe(),
                false
        );
        SupervisorAgent supervisorAgent = supervisorAgentReturning(artifactStore, supervisorCalled, supervisorDecision);
        FlowController flowController = new FlowController() {
            @Override
            public FlowDecision decide(
                    StageType stageType,
                    ReviewResult reviewed,
                    boolean repeatedIssue,
                    SupervisorDecision decision,
                    StageToolResultSummary toolSummary,
                    ImplementationRevisionFacts implementationFacts
            ) {
                return new FlowDecision(
                        devflow.agent.domain.WorkflowAction.ROUTE_TO_REPAIR,
                        StageType.IMPLEMENTATION,
                        new devflow.agent.loop.TransitionDecision(
                                devflow.agent.loop.TransitionReason.REPAIR_ROUTE,
                                stageType,
                                StageType.IMPLEMENTATION,
                                repeatedIssue,
                                reviewed.summary(),
                                decision
                        )
                );
            }
        };
        FlowDecisionExecutor flowDecisionExecutor = newRepairRouteDecisionExecutor(
                runRepository,
                artifactStore,
                eventLogStore,
                stageOperationExecutor
        );
        StageProgressCoordinator coordinator = new StageProgressCoordinator(
                artifactStore,
                supervisorAgent,
                flowController,
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
                new RepeatIssueDetector(repeatIssueDiagnosisAgent),
                new devflow.agent.i18n.LanguagePolicy()
        );

        var result = coordinator.progress(tempDir, runRecord);

        assertTrue(reviewerCalled.get());
        assertTrue(diagnosisCalled.get());
        assertTrue(supervisorCalled.get());
        assertTrue(contextProjected.get());
        assertNotNull(result.transitionDecision());
        assertEquals(devflow.agent.loop.TransitionReason.REPAIR_ROUTE, result.transitionDecision().reason());
        assertEquals(StageType.IMPLEMENTATION, result.transitionDecision().targetStage());
        assertEquals("当前实现缺少可验证运行证据", result.transitionDecision().summary());

        String transitionArtifact = artifactStore.readAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.TRANSITION_DECISION
        );
        assertTrue(transitionArtifact.contains("reason: REPAIR_ROUTE"));
        assertTrue(transitionArtifact.contains("targetStage: IMPLEMENTATION"));
        assertTrue(transitionArtifact.contains("reviewSummary: 当前实现缺少可验证运行证据"));
        assertTrue(transitionArtifact.contains("supervisorAction: ROUTE_TO_REPAIR"));

        String repairBrief = artifactStore.readAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.REPAIR_BRIEF
        );
        assertTrue(repairBrief.contains("修复摘要") || repairBrief.contains("Repair Brief"));

        String directive = artifactStore.readAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.stageDirective(StageType.IMPLEMENTATION)
        );
        assertTrue(directive.contains("DEVFLOW:EXECUTION_DIRECTIVES:BEGIN"));
        assertTrue(directive.contains("回 implementation 修复当前缺口"));

        String eventLog = eventLogStore.read(tempDir, runRecord.runId());
        assertTrue(
                eventLog.contains("阶段｜监督决策｜动作=ROUTE_TO_REPAIR｜目标阶段=IMPLEMENTATION｜模式=REWORK｜重复问题=false｜原因=REPAIR_ROUTE"),
                eventLog
        );
        assertTrue(
                eventLog.contains("阶段｜回流｜来源阶段=CODE_REVIEW｜目标阶段=IMPLEMENTATION｜修复模式=REWORK"),
                eventLog
        );
        assertTrue(
                eventLog.contains("诊断｜已触发｜来源阶段=CODE_REVIEW｜产物=repair_brief.md"),
                eventLog
        );
        assertTrue(
                eventLog.contains("阶段｜进入开始｜阶段=IMPLEMENTATION｜尝试=2"),
                eventLog
        );
        assertTrue(
                eventLog.contains("阶段｜已进入｜阶段=IMPLEMENTATION｜尝试=2"),
                eventLog
        );
        assertTrue(
                eventLog.contains("阶段｜产物生成完成｜阶段=IMPLEMENTATION｜尝试=2"),
                eventLog
        );

        RunRecord reloaded = runRepository.findById(tempDir, runRecord.runId()).orElseThrow();
        StageExecution codeReviewExecution = reloaded.stageStates().get(StageType.CODE_REVIEW);
        StageExecution implementationExecution = reloaded.stageStates().get(StageType.IMPLEMENTATION);
        assertEquals(RunStatus.IN_PROGRESS, reloaded.status());
        assertEquals(StageType.IMPLEMENTATION, reloaded.currentStage());
        assertEquals(StageStatus.NEEDS_REVISION, codeReviewExecution.status());
        assertEquals(ReviewDecision.REJECTED, codeReviewExecution.reviewDecision());
        assertEquals("当前实现缺少可验证运行证据", codeReviewExecution.reviewSummary());
        assertEquals("回 implementation 修复当前缺口", codeReviewExecution.changeRequest());
        assertEquals(StageStatus.RUNNING, implementationExecution.status());
        assertEquals(2, implementationExecution.attempt());
        assertNotNull(implementationExecution.artifactPath());
        assertTrue(artifactStore.readArtifact(tempDir, runRecord.runId(), StageType.IMPLEMENTATION).contains("Repair Implementation"));
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
                if (systemPrompt != null && systemPrompt.contains("DiagnosisAgent")) {
                    return """
                            {
                              "failureCluster": "repair-route",
                              "repeatedErrors": [],
                              "rootCauseHypothesis": "需要回 implementation 修复当前缺口",
                              "affectedFiles": ["index.html"],
                              "evidence": ["route-to-repair"],
                              "recommendedMode": "REWORK",
                              "mustFixFirst": ["只修当前批准范围"],
                              "forbiddenDirections": ["不要扩张到未批准文件"],
                              "doNotChange": [],
                              "acceptanceTarget": ["恢复当前阶段闭环"],
                              "acceptanceChecks": ["重新验证当前实现"]
                            }
                            """;
                }
                return "";
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                return generate(systemPrompt, userPrompt, options);
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
            root.put("continuationMode", payload.continuationMode() == null ? ImplementationContinuationMode.MID_PLAN_CONTINUE.name() : payload.continuationMode().name());
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

    private FlowDecisionExecutor newRepairRouteDecisionExecutor(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageOperationExecutor stageOperationExecutor
    ) {
        StageStatusSupport stageStatusSupport = new StageStatusSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                new StageFlowPolicy(),
                new WorkflowArtifactRenderer()
        );
        StageRevisionSupport stageRevisionSupport = new StageRevisionSupport(
                artifactStore,
                eventLogStore,
                new StageFlowPolicy(),
                new WorkflowArtifactRenderer(),
                new SupervisorGuidanceRenderer(),
                new StageRevisionRepairSupport(
                        artifactStore,
                        eventLogStore,
                        new DiagnosisAgent(fakeProvider(), artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        new StageRevisionNoteBuilder(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                stageStatusSupport,
                new devflow.agent.i18n.LanguagePolicy()
        );
        StageTransitionSupport stageTransitionSupport = new StageTransitionSupport(
                stageStatusSupport,
                stageRevisionSupport,
                new StageContinuationNoteBuilder()
        );
        StageEntryExecutor stageEntryExecutor = new StageEntryExecutor(
                runRepository,
                artifactStore,
                eventLogStore,
                stageOperationExecutor,
                stageTransitionSupport
        );
        return new FlowDecisionExecutor(stageTransitionSupport, stageEntryExecutor);
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
