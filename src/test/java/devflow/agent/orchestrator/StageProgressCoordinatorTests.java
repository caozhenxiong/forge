package devflow.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ArtifactSummaryBuilder;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ProjectedContext;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.GenerationEngine;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
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
                    java.util.List<FileChange> overrideChanges,
                    ImplementationPatchTarget implementationPatchTarget
            ) {
                continuationSummary.set(summary);
                continuationChangeRequest.set(changeRequest);
                continuationOverrideChanges.set(overrideChanges);
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
                artifactStore,
                new FileProjectWorkspace(),
                new ArtifactSummaryBuilder(),
                new ContractExtractor(),
                new devflow.agent.context.ContextLayerAssembler()
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
                    String summary,
                    String changeRequest,
                    String evidence,
                    String actionItems,
                    java.util.List<FileChange> overrideChanges,
                    ImplementationPatchTarget implementationPatchTarget
            ) {
                continuationSummary.set(summary);
                continuationChangeRequest.set(changeRequest);
                continuationEvidence.set(evidence);
                continuationActionItems.set(actionItems);
                continuationOverrideChanges.set(overrideChanges);
                continuationPatchTarget.set(implementationPatchTarget);
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
                    java.util.List<FileChange> overrideChanges,
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
                    String summary,
                    String changeRequest,
                    String evidence,
                    String actionItems,
                    java.util.List<FileChange> overrideChanges,
                    ImplementationPatchTarget implementationPatchTarget
            ) {
                continuationSummary.set(summary);
                return currentRun;
            }
        };
        StageProgressCoordinator coordinator = new StageProgressCoordinator(
                artifactStore,
                diagnosisAgentThatSetsFlag(artifactStore, new AtomicBoolean(false)),
                supervisorAgentThatSetsFlag(artifactStore, new AtomicBoolean(false)),
                new FlowController(),
                new ContextProjector(
                        artifactStore,
                        new FileProjectWorkspace(),
                        new ArtifactSummaryBuilder(),
                        new ContractExtractor(),
                        new devflow.agent.context.ContextLayerAssembler()
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
                new StageToolResultLoader(artifactStore),
                new StageToolResultGuard()
        );

        coordinator.progress(tempDir, runRecord);

        assertFalse(reviewerCalled.get());
        assertEquals("继续修当前子任务", continuationSummary.get());
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
}
