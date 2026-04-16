package devflow.agent.orchestrator;

import devflow.agent.executor.llm.LlmProvider;

import devflow.agent.domain.GatePolicy;
import devflow.agent.domain.HumanReviewResolutionContext;
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
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.CoverageLedgerEntry;
import devflow.agent.quality.CoverageLedgerStatus;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.QualityLedger;
import devflow.agent.quality.StructureRiskReport;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;
import devflow.agent.domain.WorkflowAction;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageTransitionSupportTests {

    @TempDir
    Path tempDir;

    private java.util.List<FileChange> patchExistingOverrideChanges() {
        return java.util.List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐现有实现缺口"));
    }

    @Test
    void onStageApprovedUsesStageEntryActionForNextStage() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageTransitionSupport support = newSupport(runRepository);
        RunRecord runRecord = runRepository.save(newRunRecord(tempDir));
        ReviewResult reviewResult = new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "", "", "");
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.ADVANCE_STAGE,
                StageType.PRD,
                FixMode.NONE,
                "next",
                java.util.List.of("聚焦入口"),
                java.util.List.of(),
                java.util.List.of(),
                DeliveryPolicy.balanced(DeliveryPolicyMode.NONE),
                false
        );
        AtomicReference<StageType> capturedStage = new AtomicReference<>();
        AtomicReference<String> capturedNote = new AtomicReference<>();

        RunRecord advanced = support.onStageApproved(
                tempDir,
                runRecord,
                StageType.ANALYSIS,
                reviewResult,
                supervisorDecision,
                (draft, stageType, runStatus, note) -> {
                    capturedStage.set(stageType);
                    capturedNote.set(note);
                    return draft;
                }
        );

        assertEquals(StageType.PRD, advanced.currentStage());
        assertEquals(StageType.PRD, capturedStage.get());
        assertNotNull(capturedNote.get());
        assertTrue(capturedNote.get().contains("DEVFLOW:EXECUTION_DIRECTIVES:BEGIN"), capturedNote.get());
        assertTrue(capturedNote.get().contains("\"deliveryMode\" : \"NONE\""), capturedNote.get());
        assertTrue(capturedNote.get().contains("聚焦入口"), capturedNote.get());
    }

    @Test
    void approveHumanReviewClearsStageGateContextAfterApproval() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageTransitionSupport support = newSupport(runRepository);
        RunRecord runRecord = runRepository.save(
                newRunRecord(tempDir).withHumanReviewResolutionContext(
                        HumanReviewResolutionContext.approveStageGate("approved", "")
                ).withCurrentStage(
                        StageType.ANALYSIS,
                        RunStatus.BLOCKED,
                        stageStates(new StageExecution(
                                StageType.ANALYSIS,
                                StageStatus.AWAITING_HUMAN_REVIEW,
                                1,
                                null,
                                ReviewDecision.APPROVED,
                                "approved",
                                ""
                        )),
                        Instant.now()
                )
        );

        RunRecord approved = support.approveHumanReview(
                tempDir,
                runRecord,
                StageType.ANALYSIS,
                "tester",
                (draft, stageType, runStatus, note) -> draft
        );

        assertEquals(StageType.PRD, approved.currentStage());
        assertEquals(StageStatus.APPROVED, approved.stageStates().get(StageType.ANALYSIS).status());
        assertEquals(null, approved.humanReviewResolutionContext());
    }

    @Test
    void approveHumanReviewConfirmsRepairRouteWithoutApprovingCurrentStage() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageTransitionSupport support = newSupport(runRepository);
        RunRecord runRecord = runRepository.save(
                new RunRecord(
                        UUID.randomUUID(),
                        tempDir,
                        "goal",
                        "",
                        RunConfig.defaultConfig(),
                        StageType.TEST,
                        RunStatus.BLOCKED,
                        stageStates(new StageExecution(
                                StageType.TEST,
                                StageStatus.AWAITING_HUMAN_REVIEW,
                                1,
                                null,
                                ReviewDecision.REJECTED,
                                "测试拒绝",
                                "回 implementation 修复"
                        )),
                        HumanReviewResolutionContext.confirmRepairRoute(
                                StageType.IMPLEMENTATION,
                                FixMode.PATCH,
                                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                                patchExistingOverrideChanges(),
                                "测试拒绝",
                                "回 implementation 修复",
                                "缺少运行证据",
                                "1. 修复 2. 复测",
                                devflow.agent.protocol.ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK
                        ),
                        Instant.now(),
                        Instant.now()
                )
        );

        RunRecord rerouted = support.approveHumanReview(
                tempDir,
                runRecord,
                StageType.TEST,
                "tester",
                (draft, stageType, runStatus, note) -> draft
        );

        assertEquals(StageType.IMPLEMENTATION, rerouted.currentStage());
        assertEquals(StageStatus.NEEDS_REVISION, rerouted.stageStates().get(StageType.TEST).status());
        assertEquals(ReviewDecision.REJECTED, rerouted.stageStates().get(StageType.TEST).reviewDecision());
        assertEquals(null, rerouted.humanReviewResolutionContext());
    }

    @Test
    void rejectHumanReviewClearsStageGateContextBeforeReroute() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageTransitionSupport support = newSupport(runRepository);
        RunRecord runRecord = runRepository.save(
                newRunRecord(tempDir).withHumanReviewResolutionContext(
                        HumanReviewResolutionContext.approveStageGate("需要人工确认", "退回当前阶段修订")
                ).withCurrentStage(
                        StageType.ANALYSIS,
                        RunStatus.BLOCKED,
                        stageStates(new StageExecution(
                                StageType.ANALYSIS,
                                StageStatus.AWAITING_HUMAN_REVIEW,
                                1,
                                null,
                                ReviewDecision.REVISION_REQUIRED,
                                "需要人工确认",
                                "退回当前阶段修订"
                        )),
                        Instant.now()
                )
        );

        RunRecord rerouted = support.rejectHumanReview(
                tempDir,
                runRecord,
                StageType.ANALYSIS,
                "tester",
                "不同意当前结论",
                (draft, stageType, runStatus, note) -> runRepository.save(draft)
        );

        RunRecord reloaded = runRepository.findById(tempDir, runRecord.runId()).orElseThrow();
        assertEquals(null, rerouted.humanReviewResolutionContext());
        assertEquals(null, reloaded.humanReviewResolutionContext());
        assertEquals(StageType.ANALYSIS, reloaded.currentStage());
        assertEquals(RunStatus.IN_PROGRESS, reloaded.status());
        assertEquals(StageStatus.NEEDS_REVISION, reloaded.stageStates().get(StageType.ANALYSIS).status());
    }

    @Test
    void rerouteForRevisionFailsWhenAutoRevisionBudgetIsExhausted() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageTransitionSupport support = newSupport(runRepository);
        RunRecord runRecord = new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "",
                new RunConfig(Map.of(StageType.IMPLEMENTATION, GatePolicy.AGENT_ONLY), 1),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                stageStates(StageExecutionStatus.running(StageType.IMPLEMENTATION, 1)),
                Instant.now(),
                Instant.now()
        );
        runRepository.save(runRecord);

        RunRecord failed = support.rerouteForRevision(
                tempDir,
                runRecord,
                StageType.IMPLEMENTATION,
                new RevisionContext(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        "summary",
                        "change",
                        "evidence",
                        "items",
                        patchExistingOverrideChanges(),
                        null,
                        StageType.IMPLEMENTATION,
                        false,
                        false
                ),
                (draft, stageType, runStatus, note) -> draft
        );

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(StageStatus.FAILED, failed.stageStates().get(StageType.IMPLEMENTATION).status());
    }

    @Test
    void rerouteForRevisionRepairRouteKeepsRunStateEventsAndReentryAligned() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        LlmProvider provider = noopProvider();
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
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        new StageRevisionNoteBuilder(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                stageStatusSupport,
                new devflow.agent.i18n.LanguagePolicy()
        );
        StageTransitionSupport support = new StageTransitionSupport(
                stageStatusSupport,
                stageRevisionSupport,
                new StageContinuationNoteBuilder()
        );
        StageOperationExecutor stageOperationExecutor = new StageOperationExecutor(
                null,
                null,
                eventLogStore,
                new GenerationEngine(),
                new StageOperationPolicy()
        ) {
            @Override
            public String composeStageArtifact(
                    Path projectPath,
                    RunRecord runRecord,
                    StageType stageType,
                    StageExecution stageExecution,
                    String note
            ) {
                return "# Repair Artifact\n\n已重新进入 implementation。";
            }
        };
        StageEntryExecutor stageEntryExecutor = new StageEntryExecutor(
                runRepository,
                artifactStore,
                eventLogStore,
                stageOperationExecutor,
                support
        );
        RunRecord runRecord = runRepository.save(
                new RunRecord(
                        UUID.randomUUID(),
                        tempDir,
                        "goal",
                        "",
                        RunConfig.defaultConfig(),
                        StageType.TEST,
                        RunStatus.IN_PROGRESS,
                        stageStates(StageExecutionStatus.running(StageType.TEST, 1)),
                        Instant.now(),
                        Instant.now()
                )
        );
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.ROUTE_TO_REPAIR,
                StageType.IMPLEMENTATION,
                FixMode.REWORK,
                "回 implementation 修复",
                java.util.List.of("只修当前失败缺口"),
                java.util.List.of("不要重写其他阶段产物"),
                java.util.List.of("补齐可验证实现证据"),
                DeliveryPolicy.patchSafe(),
                false
        );

        support.rerouteForRevision(
                tempDir,
                runRecord,
                StageType.TEST,
                new RevisionContext(
                        ReviewDecision.REJECTED,
                        FixMode.REWORK,
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        "测试阶段拒绝当前实现",
                        "回 implementation 修复当前缺口",
                        "缺少可运行证据",
                        "1. 只修当前实现缺口。 2. 修完重新测试。",
                        patchExistingOverrideChanges(),
                        supervisorDecision,
                        StageType.IMPLEMENTATION,
                        true,
                        false
                ),
                stageEntryExecutor::enterStage
        );

        RunRecord reloaded = runRepository.findById(tempDir, runRecord.runId()).orElseThrow();
        StageExecution testExecution = reloaded.stageStates().get(StageType.TEST);
        StageExecution implementationExecution = reloaded.stageStates().get(StageType.IMPLEMENTATION);
        assertEquals(RunStatus.IN_PROGRESS, reloaded.status());
        assertEquals(StageType.IMPLEMENTATION, reloaded.currentStage());
        assertEquals(StageStatus.NEEDS_REVISION, testExecution.status());
        assertEquals(ReviewDecision.REJECTED, testExecution.reviewDecision());
        assertEquals("测试阶段拒绝当前实现", testExecution.reviewSummary());
        assertEquals("回 implementation 修复当前缺口", testExecution.changeRequest());
        assertEquals(StageStatus.RUNNING, implementationExecution.status());
        assertEquals(1, implementationExecution.attempt());
        assertNotNull(implementationExecution.artifactPath());

        String eventLog = eventLogStore.read(tempDir, runRecord.runId());
        assertTrue(eventLog.contains("阶段｜回流｜来源阶段=TEST｜目标阶段=IMPLEMENTATION｜修复模式=REWORK"));
        assertTrue(eventLog.contains("诊断｜已触发｜来源阶段=TEST｜产物=repair_brief.md"));
        assertTrue(eventLog.contains("阶段｜进入开始｜阶段=IMPLEMENTATION｜尝试=1"));
        assertTrue(eventLog.contains("阶段｜已进入｜阶段=IMPLEMENTATION｜尝试=1"));
        assertTrue(eventLog.contains("阶段｜产物生成完成｜阶段=IMPLEMENTATION｜尝试=1"));

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
        assertTrue(artifactStore.readArtifact(tempDir, runRecord.runId(), StageType.IMPLEMENTATION).contains("Repair Artifact"));
    }

    @Test
    void continueStageReentersSameStageWithoutReviewWrapper() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageTransitionSupport support = newSupport(runRepository);
        RunRecord runRecord = runRepository.save(
                new RunRecord(
                        UUID.randomUUID(),
                        tempDir,
                        "goal",
                        "",
                        RunConfig.defaultConfig(),
                        StageType.IMPLEMENTATION,
                        RunStatus.IN_PROGRESS,
                        stageStates(StageExecutionStatus.running(StageType.IMPLEMENTATION, 1)),
                        Instant.now(),
                        Instant.now()
                )
        );
        AtomicReference<StageType> capturedStage = new AtomicReference<>();
        AtomicReference<String> capturedNote = new AtomicReference<>();

        RunRecord continued = support.continueStage(
                tempDir,
                runRecord,
                StageType.IMPLEMENTATION,
                new StageContinuationContext(
                        devflow.agent.protocol.ImplementationContinuationMode.MID_PLAN_CONTINUE,
                        "实现仍处于阶段中间态",
                        "继续完成剩余子任务",
                        "",
                        "",
                        java.util.List.of(new FileChange("index.html", ChangeAction.WRITE, "继续补齐入口")),
                        ImplementationPatchTarget.NONE,
                        devflow.agent.review.ReviewReasonCode.NONE
                ),
                (draft, stageType, runStatus, note) -> {
                    capturedStage.set(stageType);
                    capturedNote.set(note);
                    return draft;
                }
        );

        assertEquals(StageType.IMPLEMENTATION, continued.currentStage());
        assertEquals(StageType.IMPLEMENTATION, capturedStage.get());
        assertTrue(capturedNote.get().contains("阶段中间态"));
        assertTrue(capturedNote.get().contains("继续完成剩余子任务"));
        ExecutionDirectivePayload payload = ExecutionDirectiveProtocol.parseMerged(capturedNote.get());
        assertEquals(1, payload.overrideChanges().size());
        assertEquals("index.html", payload.overrideChanges().getFirst().path());
    }

    @Test
    void rerouteForRevisionProducesSingleParsableExecutionDirectiveBlock() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        StageTransitionSupport support = newSupport(runRepository);
        RunRecord runRecord = runRepository.save(newRunRecord(tempDir));
        SupervisorDecision supervisorDecision = new SupervisorDecision(
                WorkflowAction.RETRY_STAGE,
                StageType.IMPLEMENTATION,
                FixMode.PATCH,
                "继续补齐当前实现阶段的缺口",
                java.util.List.of("完成方块控制"),
                java.util.List.of("不要推翻现有入口"),
                java.util.List.of("核心逻辑已实现的直接证据"),
                new DeliveryPolicy(DeliveryPolicyMode.PATCH, 2, 4, true, false, true),
                false
        );
        AtomicReference<String> capturedNote = new AtomicReference<>();

        support.rerouteForRevision(
                tempDir,
                runRecord,
                StageType.IMPLEMENTATION,
                new RevisionContext(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        "实现阶段未完成",
                        "继续完成剩余子任务",
                        "缺少核心逻辑证据",
                        support.mergeActionItems("继续补齐未完成项", supervisorDecision),
                        patchExistingOverrideChanges(),
                        supervisorDecision,
                        StageType.IMPLEMENTATION,
                        false,
                        false
                ),
                (draft, stageType, runStatus, note) -> {
                    capturedNote.set(note);
                    return draft;
                }
        );

        String note = capturedNote.get();
        assertNotNull(note);
        assertEquals(1, note.split("DEVFLOW:EXECUTION_DIRECTIVES:BEGIN", -1).length - 1, note);
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(note);
        assertEquals("PATCH", directives.fixMode());
        assertEquals("PATCH", directives.deliveryMode());
        assertEquals("RETRY_STAGE", directives.supervisorAction());
        assertFalse(note.contains("\"actionItems\" : \"<!-- DEVFLOW:EXECUTION_DIRECTIVES:BEGIN"), note);
    }

    @Test
    void testRevisionFeedsMissingRequiredCapabilitySurfacesIntoExecutionDirectives() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        StageTransitionSupport support = newSupport(runRepository);
        RunRecord runRecord = new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "",
                RunConfig.defaultConfig(),
                StageType.TEST,
                RunStatus.IN_PROGRESS,
                stageStates(StageExecutionStatus.running(StageType.TEST, 1)),
                Instant.now(),
                Instant.now()
        );
        runRepository.save(runRecord);
        artifactStore.writeArtifact(
                tempDir,
                runRecord.runId(),
                StageType.TEST,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.QUALITY_LEDGER,
                        new QualityLedger(
                                StructureRiskReport.low(),
                                CapabilityMatrix.empty(),
                                new CoverageLedger(java.util.List.of(
                                        new CoverageLedgerEntry(
                                                CapabilityIds.TIMED_STATE_PROGRESSION,
                                                true,
                                                CoverageLedgerStatus.MISSING,
                                                java.util.List.of(),
                                                "missing"
                                        )
                                ))
                        )
                )
        );
        AtomicReference<String> capturedNote = new AtomicReference<>();

        support.rerouteForRevision(
                tempDir,
                runRecord,
                StageType.TEST,
                new RevisionContext(
                        ReviewDecision.REJECTED,
                        FixMode.PATCH,
                        ImplementationPatchTarget.NONE,
                        "测试缺失能力覆盖",
                        "补齐时间驱动进度",
                        "missingExperienceCoverage=timed-state-progression",
                        "补齐实现并重测",
                        java.util.List.of(),
                        null,
                        StageType.IMPLEMENTATION,
                        false,
                        false
                ),
                (draft, stageType, runStatus, note) -> {
                    capturedNote.set(note);
                    return draft;
                }
        );

        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(capturedNote.get());
        assertTrue(directives.requiredCapabilitySurfaces().contains(CapabilityIds.TIMED_STATE_PROGRESSION));
    }

    private StageTransitionSupport newSupport(FileRunRepository runRepository) {
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "{}";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "", "", "");
            }
        };
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
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        new StageRevisionNoteBuilder(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                stageStatusSupport,
                new devflow.agent.i18n.LanguagePolicy()
        );
        return new StageTransitionSupport(
                stageStatusSupport,
                stageRevisionSupport,
                new StageContinuationNoteBuilder()
        );
    }

    private RunRecord newRunRecord(Path projectPath) {
        return new RunRecord(
                UUID.randomUUID(),
                projectPath,
                "goal",
                "",
                RunConfig.defaultConfig(),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                stageStates(StageExecutionStatus.running(StageType.ANALYSIS, 1)),
                Instant.now(),
                Instant.now()
        );
    }

    private Map<StageType, StageExecution> stageStates(StageExecution currentExecution) {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(currentExecution.stageType(), currentExecution);
        return states;
    }

    private static final class StageExecutionStatus {
        private StageExecutionStatus() {
        }

        private static StageExecution running(StageType stageType, int attempt) {
            return new StageExecution(stageType, StageStatus.RUNNING, attempt, null, null, null, null);
        }
    }

    private LlmProvider noopProvider() {
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
                              "mustFixFirst": ["只修当前实现缺口"],
                              "forbiddenDirections": ["不要重写其他阶段产物"],
                              "doNotChange": [],
                              "acceptanceTarget": ["恢复可运行实现"],
                              "acceptanceChecks": ["重新验证当前阶段"]
                            }
                            """;
                }
                return "{}";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "", "", "");
            }
        };
    }
}
