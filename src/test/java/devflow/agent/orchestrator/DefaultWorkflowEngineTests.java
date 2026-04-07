package devflow.agent.orchestrator;

import devflow.agent.artifact.ArtifactTemplateFactory;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.artifact.StageArtifactComposer;
import devflow.agent.context.ArtifactSummaryBuilder;
import devflow.agent.context.ContextProjector;
import devflow.agent.executor.ImplementationExecutor;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.TestExecutor;
import devflow.agent.loop.AgentLoop;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StageReviewer;
import devflow.agent.supervisor.SupervisorAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWorkflowEngineTests {

    @TempDir
    Path tempDir;

    private SupervisorAgent newSupervisorAgent(LlmProvider provider, FileArtifactStore artifactStore, FileProjectWorkspace workspace) {
        return new SupervisorAgent(
                provider,
                artifactStore,
                new ObjectMapper(),
                new ContextProjector(artifactStore, workspace, new ArtifactSummaryBuilder())
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
                new ContextProjector(artifactStore, workspace, new ArtifactSummaryBuilder()),
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
                new StageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore
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
    void approveUntilCodeReviewBuildsImplementationArtifacts() throws Exception {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(runRepository, workspace);
        LlmProvider provider = fakeProvider();
        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        StageArtifactComposer stageArtifactComposer =
                new StageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore
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
        RunRecord prdPendingReview = workflowEngine.approveStage(tempDir, runId, StageType.ANALYSIS, "tester");
        RunRecord designPendingReview = workflowEngine.approveStage(tempDir, runId, StageType.PRD, "tester");
        RunRecord codeReviewPendingReview = workflowEngine.approveStage(tempDir, runId, StageType.DESIGN, "tester");

        assertEquals(StageType.PRD, prdPendingReview.currentStage());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, prdPendingReview.stageStates().get(StageType.PRD).status());

        assertEquals(StageType.DESIGN, designPendingReview.currentStage());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, designPendingReview.stageStates().get(StageType.DESIGN).status());

        assertEquals(RunStatus.BLOCKED, codeReviewPendingReview.status());
        assertEquals(StageType.CODE_REVIEW, codeReviewPendingReview.currentStage());
        assertEquals(StageStatus.APPROVED, codeReviewPendingReview.stageStates().get(StageType.IMPLEMENTATION).status());
        assertEquals(StageStatus.AWAITING_HUMAN_REVIEW, codeReviewPendingReview.stageStates().get(StageType.CODE_REVIEW).status());
        assertTrue(Files.exists(tempDir.resolve("src/main/java/demo/App.java")));
        assertTrue(Files.exists(tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("implementation.md")));
        assertTrue(Files.exists(tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("code_review.md")));
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
                new StageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore
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
                new StageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, new FileProjectWorkspace(), new ObjectMapper(), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                        new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()),
                        new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace())
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
                """
                        ## attempt=1 reviewer=agent stage=IMPLEMENTATION

                        - decision: REVISION_REQUIRED
                        - fixMode: PATCH
                        - summary: 实现没有收敛
                        - changeRequest: 请修复同一处问题

                        """
        );
        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                """
                        ## attempt=2 reviewer=agent stage=IMPLEMENTATION

                        - decision: REVISION_REQUIRED
                        - fixMode: PATCH
                        - summary: 实现没有收敛
                        - changeRequest: 请修复同一处问题

                        """
        );
        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                """
                        ## attempt=3 reviewer=agent stage=IMPLEMENTATION

                        - decision: REVISION_REQUIRED
                        - fixMode: PATCH
                        - summary: 实现没有收敛
                        - changeRequest: 请修复同一处问题

                        """
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
                new StageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, new FileProjectWorkspace(), new ObjectMapper(), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                        new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()),
                        new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace())
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
                """
                        ## attempt=3 reviewer=agent stage=IMPLEMENTATION

                        - decision: REJECTED
                        - fixMode: REWORK
                        - summary: 引擎未实现6×6规格支持，核心功能缺失
                        - changeRequest: 需要完整实现6×6和9×9规格支持，修复引擎核心逻辑

                        """
        );
        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                """
                        ## attempt=4 reviewer=agent stage=IMPLEMENTATION

                        - decision: REJECTED
                        - fixMode: REWORK
                        - summary: 引擎未实现6×6规格支持，核心功能缺失
                        - changeRequest: 需完整实现6×6规格支持，包括初始化、生成、验证等功能

                        """
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
                new StageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                        testExecutor,
                        snapshotStore
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
        } catch (IllegalStateException ignored) {
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
                new StageArtifactComposer(
                        new ArtifactTemplateFactory(),
                        artifactStore,
                        provider,
                        new ImplementationExecutor(provider, new FileProjectWorkspace(), new ObjectMapper(), new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())),
                        new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()),
                        new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace())
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
                """
                        ## attempt=3 reviewer=agent stage=IMPLEMENTATION

                        - decision: REJECTED
                        - fixMode: REWORK
                        - summary: 数独引擎没有真正支持 6x6 棋盘
                        - changeRequest: 请补齐 6x6 初始化、生成与校验逻辑

                        """
        );
        artifactStore.appendReviewHistory(
                tempDir,
                created.runId(),
                StageType.IMPLEMENTATION,
                """
                        ## attempt=4 reviewer=agent stage=IMPLEMENTATION

                        - decision: REJECTED
                        - fixMode: REWORK
                        - summary: 6x6 模式仍然不可用，核心规格支持缺失
                        - changeRequest: 需要完整补齐 6x6 题目生成与验证能力

                        """
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
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "创建一个最小 Java 类作为实现占位。",
                              "subtasks": [
                                {
                                  "title": "创建 App 类",
                                  "goal": "提供一个最小可运行的实现文件",
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
                if (userPrompt.contains("文件路径：")) {
                    return """
                            package demo;

                            public class App {
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
                            自动化交付流程需要一个可追溯的内核。

                            ## 2. 目标与成功标准
                            目标是跑通基础工作流。

                            ## 3. 关键约束
                            第一版仅验证最小 Java 内核。

                            ## 4. 初步调研与假设
                            假设单体架构足以支撑首版。

                            ## 5. 边界与非目标
                            不做完整平台化能力。

                            ## 6. 风险与待确认问题
                            需要验证状态机与产物落盘是否一致。
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
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
    }

    private LlmProvider failingImplementationProvider() {
        return new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "创建一个最小 Java 类作为实现占位。",
                              "subtasks": [
                                {
                                  "title": "创建 App 类",
                                  "goal": "提供一个最小可运行的实现文件",
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
                if (userPrompt.contains("文件路径：")) {
                    return """
                            package demo;

                            public class App {
                                public String message() {
                                    return "broken";
                                }
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
                            自动化交付流程需要一个可追溯的内核。

                            ## 2. 目标与成功标准
                            目标是跑通基础工作流。

                            ## 3. 关键约束
                            第一版仅验证最小 Java 内核。

                            ## 4. 初步调研与假设
                            假设单体架构足以支撑首版。

                            ## 5. 边界与非目标
                            不做完整平台化能力。

                            ## 6. 风险与待确认问题
                            需要验证状态机与产物落盘是否一致。
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
}
