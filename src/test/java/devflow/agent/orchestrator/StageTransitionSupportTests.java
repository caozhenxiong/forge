package devflow.agent.orchestrator;

import devflow.agent.executor.llm.LlmProvider;

import devflow.agent.domain.GatePolicy;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
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
import devflow.agent.supervisor.SupervisorAction;
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
                SupervisorAction.ADVANCE_STAGE,
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
                false,
                (draft, stageType, runStatus, note) -> draft
        );

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(StageStatus.FAILED, failed.stageStates().get(StageType.IMPLEMENTATION).status());
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
                "实现仍处于阶段中间态",
                "继续完成剩余子任务",
                "",
                "",
                java.util.List.of(new FileChange("index.html", ChangeAction.WRITE, "继续补齐入口")),
                ImplementationPatchTarget.NONE,
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
                SupervisorAction.RETRY_STAGE,
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
                false,
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
                false,
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
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "{}";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "", "", "");
            }
        };
        return new StageTransitionSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                new RepairAgent(),
                new StageFlowPolicy(),
                new WorkflowArtifactRenderer()
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
}
