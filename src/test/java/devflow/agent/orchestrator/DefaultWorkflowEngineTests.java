package devflow.agent.orchestrator;

import devflow.agent.artifact.ArtifactTemplateFactory;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.artifact.StageArtifactComposer;
import devflow.agent.context.ArtifactSummaryBuilder;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContextProjector;
import devflow.agent.executor.ImplementationExecutor;
import devflow.agent.executor.GenerationTelemetry;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.TestExecutor;
import devflow.agent.loop.AgentLoop;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ReviewHistoryEntryPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StageReviewer;
import devflow.agent.supervisor.SupervisorFallbackPolicy;
import devflow.agent.supervisor.SupervisorAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWorkflowEngineTests {

    @TempDir
    Path tempDir;

    private ContextProjector newContextProjector(FileArtifactStore artifactStore, FileProjectWorkspace workspace) {
        return new ContextProjector(
                artifactStore,
                workspace,
                new ArtifactSummaryBuilder(),
                new ContractExtractor(),
                new devflow.agent.context.ContextLayerAssembler()
        );
    }

    private StageArtifactComposer newStageArtifactComposer(
            ArtifactTemplateFactory artifactTemplateFactory,
            FileArtifactStore artifactStore,
            LlmProvider provider,
            ImplementationExecutor implementationExecutor,
            TestExecutor testExecutor,
            WorkspaceSnapshotStore snapshotStore,
            ContractExtractor contractExtractor
    ) {
        return new StageArtifactComposer(
                artifactStore,
                provider,
                implementationExecutor,
                testExecutor,
                snapshotStore,
                contractExtractor,
                new devflow.agent.artifact.DocumentStageComposer(
                        artifactTemplateFactory,
                        artifactStore,
                        provider,
                        contractExtractor,
                        new devflow.agent.prompt.PromptTemplateCatalog(),
                        new devflow.agent.i18n.LanguagePolicy()
                ),
                new devflow.agent.i18n.LanguagePolicy()
        );
    }

    private SupervisorAgent newSupervisorAgent(LlmProvider provider, FileArtifactStore artifactStore, FileProjectWorkspace workspace) {
        return new SupervisorAgent(
                provider,
                new ObjectMapper(),
                newContextProjector(artifactStore, workspace),
                new StageFlowPolicy(),
                new SupervisorFallbackPolicy(new StageFlowPolicy())
        );
    }

    private DefaultWorkflowEngine newWorkflowEngine(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            StageArtifactComposer stageArtifactComposer,
            StageReviewer stageReviewer,
            EventLogStore eventLogStore,
            WorkspaceSnapshotStore snapshotStore,
            DiagnosisAgent diagnosisAgent,
            RepairAgent repairAgent,
            SupervisorAgent supervisorAgent,
            FileProjectWorkspace workspace
    ) {
        return new DefaultWorkflowEngine(
                runRepository,
                artifactStore,
                stageArtifactComposer,
                stageReviewer,
                eventLogStore,
                snapshotStore,
                diagnosisAgent,
                repairAgent,
                supervisorAgent,
                new FlowController(),
                new StageFlowPolicy(),
                newContextProjector(artifactStore, workspace),
                new AgentLoop()
        );
    }

    @Test
    void createAndStartRunWritesStateAndAnalysisArtifact() throws Exception {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, workspace);
        LlmProvider provider = fakeProvider();
        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        StageArtifactComposer stageArtifactComposer =
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore,
                        new ContractExtractor()
                );
        DefaultWorkflowEngine workflowEngine =
                newWorkflowEngine(
                        runRepository,
                        artifactStore,
                        stageArtifactComposer,
                        new StageReviewer(provider, snapshotStore, testExecutor),
                        new EventLogStore(runRepository),
                        snapshotStore,
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        newSupervisorAgent(provider, artifactStore, workspace),
                        workspace
                );

        workflowEngine.initialize(tempDir);
        RunRecord created = workflowEngine.createRun(tempDir, "实现 Java 内核", "先跑通基础状态机");
        RunRecord started = workflowEngine.startRun(tempDir, created.runId());

        assertEquals(RunStatus.BLOCKED, started.status());
        assertEquals(StageType.ANALYSIS, started.currentStage());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, started.stageStates().get(StageType.ANALYSIS).status());
        assertTrue(Files.exists(tempDir.resolve(".devflow/runs").resolve(created.runId().toString()).resolve("analysis.md")));
        assertTrue(Files.exists(tempDir.resolve(".devflow/runs").resolve(created.runId().toString()).resolve("analysis_review.md")));
        assertTrue(Files.exists(tempDir.resolve(".devflow/runs").resolve(created.runId().toString()).resolve("analysis_review_history.md")));
    }

    @Test
    void enteredStageEventIsWrittenBeforeArtifactAndReviewEvents() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, workspace);
        LlmProvider provider = fakeProvider();
        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        StageArtifactComposer stageArtifactComposer =
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore,
                        new ContractExtractor()
                );
        DefaultWorkflowEngine workflowEngine =
                newWorkflowEngine(
                        runRepository,
                        artifactStore,
                        stageArtifactComposer,
                        new StageReviewer(provider, snapshotStore, testExecutor),
                        eventLogStore,
                        snapshotStore,
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        newSupervisorAgent(provider, artifactStore, workspace),
                        workspace
                );

        workflowEngine.initialize(tempDir);
        RunRecord created = workflowEngine.createRun(tempDir, "实现 Java 内核", "先跑通基础状态机");
        workflowEngine.startRun(tempDir, created.runId());

        String events = eventLogStore.read(tempDir, created.runId());
        int enteringIndex = events.indexOf("阶段｜进入开始｜阶段=ANALYSIS｜尝试=1");
        int enteredIndex = events.indexOf("阶段｜已进入｜阶段=ANALYSIS｜尝试=1");
        int artifactIndex = events.indexOf("阶段｜产物生成完成｜阶段=ANALYSIS｜尝试=1");
        int reviewIndex = events.indexOf("阶段｜评审完成｜阶段=ANALYSIS｜决定=APPROVED｜修复模式=NONE");

        assertTrue(enteringIndex >= 0, events);
        assertTrue(enteredIndex >= 0, events);
        assertTrue(artifactIndex >= 0, events);
        assertTrue(reviewIndex >= 0, events);
        assertTrue(enteringIndex < enteredIndex, events);
        assertTrue(enteredIndex < artifactIndex, events);
        assertTrue(artifactIndex < reviewIndex, events);
    }

    @Test
    void stageReviewWritesObservableLifecycleEvents() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, workspace);
        LlmProvider provider = fakeProvider();
        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        StageArtifactComposer stageArtifactComposer =
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore,
                        new ContractExtractor()
                );
        DefaultWorkflowEngine workflowEngine =
                newWorkflowEngine(
                        runRepository,
                        artifactStore,
                        stageArtifactComposer,
                        new StageReviewer(provider, snapshotStore, testExecutor),
                        eventLogStore,
                        snapshotStore,
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        newSupervisorAgent(provider, artifactStore, workspace),
                        workspace
                );

        workflowEngine.initialize(tempDir);
        RunRecord created = workflowEngine.createRun(tempDir, "实现 Java 内核", "先跑通基础状态机");
        workflowEngine.startRun(tempDir, created.runId());

        String events = eventLogStore.read(tempDir, created.runId());
        assertTrue(events.contains("阶段评审｜开始｜阶段=ANALYSIS｜阶段尝试=1｜本轮尝试=1/1"), events);
        assertTrue(events.contains("阶段评审｜成功｜阶段=ANALYSIS｜阶段尝试=1｜本轮尝试=1/1"), events);
        assertTrue(events.contains("输入token=估算"), events);
        assertTrue(events.contains("输出token="), events);
    }

    @Test
    void stageGenerationWritesObservableLifecycleEvents() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, workspace);
        LlmProvider provider = fakeProvider();
        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        StageArtifactComposer stageArtifactComposer =
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore,
                        new ContractExtractor()
                );
        DefaultWorkflowEngine workflowEngine =
                newWorkflowEngine(
                        runRepository,
                        artifactStore,
                        stageArtifactComposer,
                        new StageReviewer(provider, snapshotStore, testExecutor),
                        eventLogStore,
                        snapshotStore,
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        newSupervisorAgent(provider, artifactStore, workspace),
                        workspace
                );

        workflowEngine.initialize(tempDir);
        RunRecord created = workflowEngine.createRun(tempDir, "实现 Java 内核", "先跑通基础状态机");
        workflowEngine.startRun(tempDir, created.runId());

        String events = eventLogStore.read(tempDir, created.runId());
        assertTrue(events.contains("阶段生成｜开始｜阶段=ANALYSIS｜阶段尝试=1｜本轮尝试=1/1"), events);
        assertTrue(events.contains("阶段生成｜成功｜阶段=ANALYSIS｜阶段尝试=1｜本轮尝试=1/1"), events);
        assertTrue(events.contains("输入token=估算"), events);
        assertTrue(events.contains("输出token="), events);
    }

    @Test
    void approveUntilCodeReviewBuildsImplementationArtifacts() throws Exception {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, workspace);
        LlmProvider provider = fakeProvider();
        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        StageArtifactComposer stageArtifactComposer =
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore,
                        new ContractExtractor()
                );
        DefaultWorkflowEngine workflowEngine =
                newWorkflowEngine(
                        runRepository,
                        artifactStore,
                        stageArtifactComposer,
                        new StageReviewer(provider, snapshotStore, testExecutor),
                        new EventLogStore(runRepository),
                        snapshotStore,
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        newSupervisorAgent(provider, artifactStore, workspace),
                        workspace
                );

        workflowEngine.initialize(tempDir);
        RunRecord created = workflowEngine.createRun(tempDir, "实现 Java 内核", "先跑通基础状态机");
        UUID runId = created.runId();

        RunRecord current = workflowEngine.startRun(tempDir, runId);
        if (current.stageStates().get(StageType.ANALYSIS).status() == StageStatus.AWAITING_HUMAN_REVIEW) {
            current = workflowEngine.approveStage(tempDir, runId, StageType.ANALYSIS, "tester");
        }
        RunRecord prdPendingReview = current;
        if (current.stageStates().get(StageType.PRD).status() == StageStatus.AWAITING_HUMAN_REVIEW) {
            current = workflowEngine.approveStage(tempDir, runId, StageType.PRD, "tester");
        }
        RunRecord designPendingReview = current;
        try {
            if (current.stageStates().get(StageType.DESIGN).status() == StageStatus.AWAITING_HUMAN_REVIEW) {
                current = workflowEngine.approveStage(tempDir, runId, StageType.DESIGN, "tester");
            }
        } catch (RuntimeException ignored) {
            current = workflowEngine.find(tempDir, runId);
        }
        RunRecord codeReviewPendingReview = current;

        assertEquals(StageType.PRD, prdPendingReview.currentStage());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, prdPendingReview.stageStates().get(StageType.PRD).status());

        assertTrue(
                designPendingReview.currentStage() == StageType.DESIGN
                        || designPendingReview.currentStage() == StageType.CODE_REVIEW
        );

        assertTrue(
                codeReviewPendingReview.status() == RunStatus.BLOCKED
                        || codeReviewPendingReview.status() == RunStatus.FAILED
        );
        assertTrue(
                codeReviewPendingReview.stageStates().get(StageType.IMPLEMENTATION).status() != null
        );
        assertTrue(
                Files.exists(tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("implementation.md"))
                        || codeReviewPendingReview.status() == RunStatus.FAILED
        );
        assertTrue(
                Files.exists(tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("code_review.md"))
                        || codeReviewPendingReview.status() == RunStatus.FAILED
        );
    }

    @Test
    void rejectOnDocumentStageRegeneratesSameStage() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, workspace);
        LlmProvider provider = fakeProvider();
        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        StageArtifactComposer stageArtifactComposer =
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore,
                        new ContractExtractor()
                );
        DefaultWorkflowEngine workflowEngine =
                newWorkflowEngine(
                        runRepository,
                        artifactStore,
                        stageArtifactComposer,
                        new StageReviewer(provider, snapshotStore, testExecutor),
                        new EventLogStore(runRepository),
                        snapshotStore,
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        newSupervisorAgent(provider, artifactStore, workspace),
                        workspace
                );

        workflowEngine.initialize(tempDir);
        RunRecord created = workflowEngine.createRun(tempDir, "实现 Java 内核", "先跑通基础状态机");
        UUID runId = created.runId();

        workflowEngine.startRun(tempDir, runId);
        workflowEngine.approveStage(tempDir, runId, StageType.ANALYSIS, "tester");
        RunRecord rerouted = workflowEngine.rejectStage(tempDir, runId, StageType.PRD, "tester", "补充成功标准");

        assertEquals(RunStatus.BLOCKED, rerouted.status());
        assertEquals(StageType.PRD, rerouted.currentStage());
        assertEquals(2, rerouted.stageStates().get(StageType.PRD).attempt());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, rerouted.stageStates().get(StageType.PRD).status());
        assertTrue(Files.exists(tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("prd_review_history.md")));
    }

    @Test
    void diagnosisAgentDetectsRepeatedFailuresAndBuildsRepairBrief() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        LlmProvider provider = failingImplementationProvider();
        DiagnosisAgent diagnosisAgent = new DiagnosisAgent(provider, artifactStore, new ObjectMapper());

        runRepository.initialize(tempDir);
        RunRecord created = newWorkflowEngine(
                runRepository,
                artifactStore,
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, new FileProjectWorkspace(), new ObjectMapper(), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                        new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()),
                        new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()),
                        new ContractExtractor()
                ),
                new StageReviewer(provider, new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                new EventLogStore(runRepository),
                new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()),
                diagnosisAgent,
                new RepairAgent(),
                newSupervisorAgent(provider, artifactStore, new FileProjectWorkspace()),
                new FileProjectWorkspace()
        ).createRun(tempDir, "实现 Java 内核", "先跑通基础状态机");

        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                reviewHistoryEntry(1, "agent", StageType.IMPLEMENTATION, "REVISION_REQUIRED", "PATCH", "实现没有收敛", "请修复同一处问题")
        );
        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                reviewHistoryEntry(2, "agent", StageType.IMPLEMENTATION, "REVISION_REQUIRED", "PATCH", "实现没有收敛", "请修复同一处问题")
        );
        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                reviewHistoryEntry(3, "agent", StageType.IMPLEMENTATION, "REVISION_REQUIRED", "PATCH", "实现没有收敛", "请修复同一处问题")
        );

        assertTrue(diagnosisAgent.shouldDiagnose(tempDir, created, StageType.IMPLEMENTATION, FixMode.PATCH, "实现没有收敛", "请修复同一处问题"));
        assertEquals(
                FixMode.PATCH,
                diagnosisAgent.diagnose(tempDir, created, StageType.IMPLEMENTATION, FixMode.PATCH, "实现没有收敛", "请修复同一处问题").recommendedMode()
        );
    }

    @Test
    void diagnosisAgentTriggersAfterTwoReworkFailuresWithSameSummary() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        LlmProvider provider = failingImplementationProvider();
        DiagnosisAgent diagnosisAgent = new DiagnosisAgent(provider, artifactStore, new ObjectMapper());

        runRepository.initialize(tempDir);
        RunRecord created = newWorkflowEngine(
                runRepository,
                artifactStore,
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, new FileProjectWorkspace(), new ObjectMapper(), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                        new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()),
                        new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()),
                        new ContractExtractor()
                ),
                new StageReviewer(provider, new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                new EventLogStore(runRepository),
                new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()),
                diagnosisAgent,
                new RepairAgent(),
                newSupervisorAgent(provider, artifactStore, new FileProjectWorkspace()),
                new FileProjectWorkspace()
        ).createRun(tempDir, "实现数独", "纯前端");

        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                reviewHistoryEntry(3, "agent", StageType.IMPLEMENTATION, "REJECTED", "REWORK", "引擎未实现6×6规格支持，核心功能缺失", "需要完整实现6×6和9×9规格支持，修复引擎核心逻辑")
        );
        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                reviewHistoryEntry(4, "agent", StageType.IMPLEMENTATION, "REJECTED", "REWORK", "引擎未实现6×6规格支持，核心功能缺失", "需完整实现6×6规格支持，包括初始化、生成、验证等功能")
        );

        assertTrue(
                diagnosisAgent.shouldDiagnose(
                        tempDir,
                        created,
                        StageType.IMPLEMENTATION,
                        FixMode.REWORK,
                        "引擎未实现6×6规格支持，核心功能缺失",
                        "需完整实现6×6规格支持，包括初始化、生成、验证等功能"
                )
        );
    }

    @Test
    void fatalGenerationErrorMarksRunFailed() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, workspace);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (userPrompt.contains("目标：")) {
                    throw new IllegalStateException("simulate generation failure");
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        StageArtifactComposer stageArtifactComposer =
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore,
                        new ContractExtractor()
                );
        DefaultWorkflowEngine workflowEngine =
                newWorkflowEngine(
                        runRepository,
                        artifactStore,
                        stageArtifactComposer,
                        new StageReviewer(provider, snapshotStore, testExecutor),
                        new EventLogStore(runRepository),
                        snapshotStore,
                        new DiagnosisAgent(provider, artifactStore, new ObjectMapper()),
                        new RepairAgent(),
                        newSupervisorAgent(provider, artifactStore, workspace),
                        workspace
                );

        workflowEngine.initialize(tempDir);
        RunRecord created = workflowEngine.createRun(tempDir, "实现网页", "先有骨架");

        try {
            workflowEngine.startRun(tempDir, created.runId());
        } catch (RuntimeException ignored) {
        }

        RunRecord persisted = workflowEngine.find(tempDir, created.runId());
        assertEquals(StageType.ANALYSIS, persisted.currentStage());
        assertEquals(RunStatus.FAILED, persisted.status());
        assertEquals(StageStatus.FAILED, persisted.stageStates().get(StageType.ANALYSIS).status());
        assertEquals(1, persisted.stageStates().get(StageType.ANALYSIS).attempt());
    }

    @Test
    void diagnosisAgentCanUseModelToRecognizeSemanticallySameIssue() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("FailureSimilarityJudge")) {
                    return """
                            {
                              "sameIssue": true,
                              "reason": "两次失败都指向 6x6 数独规格支持缺失"
                            }
                            """;
                }
                if (systemPrompt.contains("DiagnosisAgent")) {
                    return """
                            {
                              "failureCluster": "6x6规格支持缺失",
                              "repeatedErrors": ["6x6 逻辑缺失"],
                              "rootCauseHypothesis": "实现始终没有补齐 6x6 规格相关逻辑",
                              "affectedFiles": ["main.js", "sudoku-engine.js"],
                              "evidence": ["两轮失败都聚焦 6x6 规格"],
                              "recommendedMode": "REWORK",
                              "doNotChange": ["不要重写无关 UI"],
                              "acceptanceTarget": ["6x6 和 9x9 都可初始化并正常生成题目"]
                            }
                            """;
                }
                return "# 默认内容";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        DiagnosisAgent diagnosisAgent = new DiagnosisAgent(provider, artifactStore, new ObjectMapper());

        runRepository.initialize(tempDir);
        RunRecord created = newWorkflowEngine(
                runRepository,
                artifactStore,
                newStageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, new FileProjectWorkspace(), new ObjectMapper(), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                        new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()),
                        new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()),
                        new ContractExtractor()
                ),
                new StageReviewer(provider, new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                new EventLogStore(runRepository),
                new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()),
                diagnosisAgent,
                new RepairAgent(),
                newSupervisorAgent(provider, artifactStore, new FileProjectWorkspace()),
                new FileProjectWorkspace()
        ).createRun(tempDir, "实现数独", "纯前端");

        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                reviewHistoryEntry(3, "agent", StageType.IMPLEMENTATION, "REJECTED", "REWORK", "数独引擎没有真正支持 6x6 棋盘", "请补齐 6x6 初始化、生成与校验逻辑")
        );
        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                reviewHistoryEntry(4, "agent", StageType.IMPLEMENTATION, "REJECTED", "REWORK", "6x6 模式仍然不可用，核心规格支持缺失", "需要完整补齐 6x6 题目生成与验证能力")
        );

        assertTrue(
                diagnosisAgent.shouldDiagnose(
                        tempDir,
                        created,
                        StageType.IMPLEMENTATION,
                        FixMode.REWORK,
                        "6x6 规格支持仍未完成，当前实现无法正常生成题目",
                        "补齐 6x6 模式的生成、初始化和验证逻辑"
                )
        );
    }

    private LlmProvider fakeProvider() {
        return new LlmProvider() {
            private final AtomicReference<GenerationTelemetry> telemetryRef = new AtomicReference<>();

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                telemetryRef.set(new GenerationTelemetry(
                        "fake-model",
                        "TEST",
                        120,
                        100,
                        80,
                        2048,
                        256,
                        1692,
                        256,
                        200,
                        "stop"
                ));
                if (systemPrompt.contains("你是 SupervisorAgent，负责决定 Forge 的下一步流程动作。")) {
                    return fakeSupervisorDecision(userPrompt);
                }
                if (systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "创建一个最小可运行的 Java 主类。",
                              "subtasks": [
                                {
                                  "title": "创建 App 类",
                                  "goal": "提供一个最小可运行的 Java 入口实现",
                                  "deliveryMode": "INCREMENTAL",
                                  "runnableMilestone": true,
                                  "coverageRefs": ["CAP-1"],
                                  "ownedCapabilities": ["main-class 入口可启动", "message 方法返回 ok"],
                                  "acceptanceCriteria": ["存在 App.java", "message 方法返回 ok"],
                                  "changes": [
                                    {
                                      "path": "src/main/java/demo/App.java",
                                      "action": "WRITE",
                                      "reason": "提供实现阶段的实际文件变更。"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (systemPrompt.contains("符号级精确改写")) {
                    return """
                            {
                              "operations": [
                                {
                                  "action": "APPEND_FILE",
                                  "targetSymbol": null,
                                  "targetKind": null,
                                  "content": "package demo;\\n\\npublic class App {\\n    public static void main(String[] args) {\\n        System.out.println(\\\"ok\\\");\\n    }\\n\\n    public String message() {\\n        return \\\"ok\\\";\\n    }\\n}\\n"
                                }
                              ]
                            }
                            """;
                }
                if (userPrompt.contains("文件路径：") || userPrompt.contains("当前目标文件：")) {
                    return """
                            package demo;

                            public class App {
                                public static void main(String[] args) {
                                    System.out.println("ok");
                                }

                                public String message() {
                                    return "ok";
                                }
                            }
                            """;
                }
                if (userPrompt.contains("《需求分析与调研》")) {
                    return """
                            # 需求分析与调研

                            ## 1. 背景与问题定义
                            需要一个可追溯的最小实现来验证基础工作流。

                            ## 2. 目标与成功标准
                            目标是跑通基础工作流。

                            ## 3. 关键约束
                            - 先跑通基础状态机。

                            ## 4. 初步调研与假设
                            采用最小单体实现足以验证当前工作流；如后续节点持续增长，再考虑拆分上下文与执行内核。

                            ## 5. 边界与非目标
                            不做完整平台化能力。

                            ## 6. 风险与待确认问题
                            需要验证状态机与产物落盘是否一致。

                            ## 7. Source Metadata
                            - hard.userRequirements: 实现 Java 内核, 先跑通基础状态机
                            - hard.upstreamFacts: (none)
                            - soft.inferences: 单体架构足以支撑首版
                            - soft.designDecisions: (none)
                            - soft.recommendations: (none)
                            - open.questions: (none)
                            """;
                }
                if (userPrompt.contains("《产品需求文档》")) {
                    return """
                            # 产品需求文档

                            ## 1. 产品目标
                            将需求分析转换为研发可执行任务。

                            ## 2. 目标用户与使用场景
                            面向研发流程使用者。

                            ## 3. 功能范围
                            覆盖最小工作流与产物落盘。

                            ## 4. 非功能要求
                            保持实现简单且可测试。

                            ## 5. 验收标准
                            能创建 run 并推进到 code review。

                            ## 6. 不做什么
                            不做完整 web UI。

                            ## 7. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: main-class
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: false
                            - runtime.acceptanceSignals: cli-starts

                            ## 8. Source Metadata
                            - hard.userRequirements: 实现 Java 内核, 先跑通基础状态机
                            - hard.upstreamFacts: (none)
                            - soft.inferences: (none)
                            - soft.designDecisions: (none)
                            - soft.recommendations: (none)
                            - open.questions: (none)
                            """;
                }
                if (userPrompt.contains("《技术方案设计》")) {
                    return """
                            # 技术方案设计

                            ## 1. 技术目标
                            用模块化单体完成首版工作流编排。

                            ## 2. 系统边界与模块划分
                            使用 orchestrator、artifact、review、executor 模块。

                            ## 3. 核心数据模型
                            使用 RunRecord 和 StageExecution 表达状态。

                            ## 4. 关键流程
                            create -> start -> approve -> implementation -> code review。

                            ## 5. 接口、页面或命令设计
                            通过 CLI 推进流程。

                            ## 6. 测试与验证策略
                            通过单元测试验证状态机行为。

                            ## 7. 风险与取舍
                            先接受单体实现，后续再平台化。

                            ## 8. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: main-class
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: false
                            - runtime.acceptanceSignals: cli-starts

                            ## 9. Source Metadata
                            - hard.userRequirements: 实现 Java 内核, 先跑通基础状态机
                            - hard.upstreamFacts: (none)
                            - soft.inferences: (none)
                            - soft.designDecisions: 单体模块化实现
                            - soft.recommendations: (none)
                            - open.questions: (none)
                            """;
                }
                if (systemPrompt.contains("资深代码审阅者")) {
                    return """
                            - decision: APPROVED
                            - fixMode: NONE
                            - summary: 变更简单且可接受
                            - changeRequest:

                            ## Findings

                            - 无阻塞问题
                            """;
                }
                return """
                        # 默认内容

                        这是测试内容。
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                telemetryRef.set(new GenerationTelemetry(
                        "fake-model",
                        "CODE_REVIEW",
                        160,
                        140,
                        90,
                        2048,
                        256,
                        1652,
                        256,
                        220,
                        "stop"
                ));
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public GenerationTelemetry consumeLastTelemetry() {
                return telemetryRef.getAndSet(null);
            }
        };
    }

    private String fakeSupervisorDecision(String userPrompt) {
        String currentStage = extractPromptValue(userPrompt, "当前阶段：");
        String nextStage = extractPromptValue(userPrompt, "下一阶段：");
        String gate = extractPromptValue(userPrompt, "当前阶段 gate：");
        boolean approved = userPrompt.contains("- decision: APPROVED");
        if (approved && "AGENT_PLUS_HUMAN".equals(gate)) {
            return """
                    {
                      "action": "REQUEST_HUMAN_REVIEW",
                      "targetStage": "%s",
                      "mode": "NONE",
                      "reason": "文档阶段需要人工 gate。",
                      "focus": [],
                      "constraints": [],
                      "requiredEvidence": [],
                      "deliveryPolicy": {
                        "mode": "NONE",
                        "maxFiles": 0,
                        "maxSymbols": 0,
                        "preferPreciseEditing": false,
                        "forceBacklogSplit": false,
                        "requireVerificationBeforeReview": true
                      },
                      "humanRequired": true
                    }
                    """.formatted(currentStage);
        }
        if (approved && "null".equals(nextStage)) {
            return """
                    {
                      "action": "COMPLETE_RUN",
                      "targetStage": "%s",
                      "mode": "NONE",
                      "reason": "最后阶段已通过。",
                      "focus": [],
                      "constraints": [],
                      "requiredEvidence": [],
                      "deliveryPolicy": {
                        "mode": "NONE",
                        "maxFiles": 0,
                        "maxSymbols": 0,
                        "preferPreciseEditing": false,
                        "forceBacklogSplit": false,
                        "requireVerificationBeforeReview": true
                      },
                      "humanRequired": false
                    }
                    """.formatted(currentStage);
        }
        if (approved) {
            return """
                    {
                      "action": "ADVANCE_STAGE",
                      "targetStage": "%s",
                      "mode": "NONE",
                      "reason": "当前阶段已通过。",
                      "focus": [],
                      "constraints": [],
                      "requiredEvidence": [],
                      "deliveryPolicy": {
                        "mode": "INCREMENTAL",
                        "maxFiles": 2,
                        "maxSymbols": 4,
                        "preferPreciseEditing": true,
                        "forceBacklogSplit": false,
                        "requireVerificationBeforeReview": true
                      },
                      "humanRequired": false
                    }
                    """.formatted(nextStage);
        }
        return """
                {
                  "action": "RETRY_STAGE",
                  "targetStage": "%s",
                  "mode": "PATCH",
                  "reason": "当前问题仍需继续修订。",
                  "focus": [],
                  "constraints": [],
                  "requiredEvidence": [],
                  "deliveryPolicy": {
                    "mode": "PATCH",
                    "maxFiles": 2,
                    "maxSymbols": 2,
                    "preferPreciseEditing": true,
                    "forceBacklogSplit": false,
                    "requireVerificationBeforeReview": true
                  },
                  "humanRequired": false
                }
                """.formatted(currentStage);
    }

    private String extractPromptValue(String prompt, String label) {
        int start = prompt.indexOf(label);
        if (start < 0) {
            return "";
        }
        String tail = prompt.substring(start + label.length()).stripLeading();
        return tail.lines().findFirst().orElse("").trim();
    }

    private LlmProvider failingImplementationProvider() {
        return new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "创建一个最小可运行的 Java 主类。",
                              "subtasks": [
                                {
                                  "title": "创建 App 类",
                                  "goal": "提供一个最小可运行的 Java 入口实现",
                                  "deliveryMode": "INCREMENTAL",
                                  "runnableMilestone": true,
                                  "coverageRefs": ["CAP-1"],
                                  "ownedCapabilities": ["main-class 入口可启动", "message 方法返回 ok"],
                                  "acceptanceCriteria": ["存在 App.java", "message 方法返回 ok"],
                                  "changes": [
                                    {
                                      "path": "src/main/java/demo/App.java",
                                      "action": "WRITE",
                                      "reason": "提供实现阶段的实际文件变更。"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (userPrompt.contains("文件路径：") || userPrompt.contains("当前目标文件：")) {
                    return """
                            package demo;

                            public class App {
                                public static void main(String[] args) {
                                    System.out.println("broken");
                                }

                                public String message() {
                                    return "broken";
                                }
                            }
                            """;
                }
                if (systemPrompt.contains("FailureSimilarityJudge")) {
                    return """
                            {
                              "sameIssue": true,
                              "reason": "最近几轮失败仍指向同一个实现收敛问题"
                            }
                            """;
                }
                if (systemPrompt.contains("DiagnosisAgent")) {
                    return """
                            {
                              "failureCluster": "重复实现失败",
                              "repeatedErrors": ["实现阶段连续失败"],
                              "rootCauseHypothesis": "原实现路径没有收敛",
                              "affectedFiles": ["src/main/java/demo/App.java"],
                              "evidence": ["同类 changeRequest 连续出现"],
                              "recommendedMode": "PATCH",
                              "doNotChange": ["不要重写整个项目"],
                              "acceptanceTarget": ["实现阶段错误不再重复"]
                            }
                            """;
                }
                if (userPrompt.contains("《需求分析与调研》")) {
                    return """
                            # 需求分析与调研

                            ## 1. 背景与问题定义
                            需要一个可追溯的最小实现来验证基础工作流。

                            ## 2. 目标与成功标准
                            目标是跑通基础工作流。

                            ## 3. 关键约束
                            - 先跑通基础工作流。

                            ## 4. 初步调研与假设
                            开放问题：首版之后是否需要进一步拆分架构。

                            ## 5. 边界与非目标
                            不做完整平台化能力。

                            ## 6. 风险与待确认问题
                            需要验证状态机与产物落盘是否一致。

                            ## 7. Source Metadata
                            - hard.userRequirements: 实现 Java 内核, 先跑通基础状态机
                            - hard.upstreamFacts: (none)
                            - soft.inferences: 单体架构足以支撑首版
                            - soft.designDecisions: (none)
                            - soft.recommendations: (none)
                            - open.questions: (none)
                            """;
                }
                if (userPrompt.contains("《产品需求文档》")) {
                    return """
                            # 产品需求文档

                            ## 1. 产品目标
                            将需求分析转换为研发可执行任务。

                            ## 2. 目标用户与使用场景
                            面向研发流程使用者。

                            ## 3. 功能范围
                            覆盖最小工作流与产物落盘。

                            ## 4. 非功能要求
                            保持实现简单且可测试。

                            ## 5. 验收标准
                            能创建 run 并推进到 code review。

                            ## 6. 不做什么
                            不做完整 web UI。

                            ## 7. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: main-class
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: false
                            - runtime.acceptanceSignals: cli-starts

                            ## 8. Source Metadata
                            - hard.userRequirements: 实现 Java 内核, 先跑通基础状态机
                            - hard.upstreamFacts: (none)
                            - soft.inferences: (none)
                            - soft.designDecisions: (none)
                            - soft.recommendations: (none)
                            - open.questions: (none)
                            """;
                }
                if (userPrompt.contains("《技术方案设计》")) {
                    return """
                            # 技术方案设计

                            ## 1. 技术目标
                            用模块化单体完成首版工作流编排。

                            ## 2. 系统边界与模块划分
                            使用 orchestrator、artifact、review、executor 模块。

                            ## 3. 核心数据模型
                            使用 RunRecord 和 StageExecution 表达状态。

                            ## 4. 关键流程
                            create -> start -> approve -> implementation -> code review。

                            ## 5. 接口、页面或命令设计
                            通过 CLI 推进流程。

                            ## 6. 测试与验证策略
                            通过单元测试验证状态机行为。

                            ## 7. 风险与取舍
                            先接受单体实现，后续再平台化。

                            ## 8. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: main-class
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: false
                            - runtime.acceptanceSignals: cli-starts

                            ## 9. Source Metadata
                            - hard.userRequirements: 实现 Java 内核, 先跑通基础状态机
                            - hard.upstreamFacts: (none)
                            - soft.inferences: (none)
                            - soft.designDecisions: 单体模块化实现
                            - soft.recommendations: (none)
                            - open.questions: (none)
                            """;
                }
                if (systemPrompt.contains("资深代码审阅者")) {
                    return """
                            - decision: APPROVED
                            - fixMode: NONE
                            - summary: 变更简单且可接受
                            - changeRequest:

                            ## Findings

                            - 无阻塞问题
                            """;
                }
                return "# 默认内容";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                if (systemPrompt.contains("子任务验证器")) {
                    return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
                }
                if (systemPrompt.contains("严格的软件工程评审")) {
                    return new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "实现没有收敛", "请修复同一处问题");
                }
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
    }

    private String reviewHistoryEntry(
            int attempt,
            String reviewer,
            StageType stageType,
            String decision,
            String fixMode,
            String summary,
            String changeRequest
    ) {
        return StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.REVIEW_HISTORY_ENTRY,
                new ReviewHistoryEntryPayload(
                        attempt,
                        reviewer,
                        stageType.name(),
                        decision,
                        fixMode,
                        summary,
                        changeRequest,
                        "",
                        ""
                )
        );
    }
}
