package devflow.agent.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.ImplementationExecutor;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.executor.TestExecutor;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StageArtifactComposerTests {

    @TempDir
    Path tempDir;

    @Test
    void prdRevisionCanFillOnlyMissingSectionsAndMergeBack() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.PRD);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.PRD && userPrompt.contains("只补齐缺失章节")) {
                    return """
                            ## 5. 验收标准

                            ### 5.1 功能验收
                            1. 支持 6x6 与 9x9 切换并可正常开始新局。

                            ### 5.2 质量验收
                            1. 页面加载后无阻塞脚本错误。

                            ## 6. 不做什么

                            ### 6.1 本轮不包含
                            1. 不做账号体系与云端同步。

                            ### 6.2 后续可扩展方向
                            1. 后续可增加难度和排行榜。
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = new StageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace)
        );

        UUID runId = UUID.randomUUID();
        artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                需要一个纯网页版数独。
                """);
        Path prdPath = artifactStore.writeArtifact(tempDir, runId, StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                已有内容

                ## 2. 目标用户与使用场景
                已有内容

                ## 3. 功能范围
                已有内容

                ## 4. 非功能要求
                已有内容

                ## 7. 当前备注
                旧备注
                """);

        RunRecord runRecord = runRecord(runId, prdPath);
        String result = composer.compose(
                tempDir,
                runRecord,
                StageType.PRD,
                "请补齐以下章节后重试：## 5. 验收标准、## 6. 不做什么"
        );

        assertTrue(result.contains("## 1. 产品目标"));
        assertTrue(result.contains("## 5. 验收标准"));
        assertTrue(result.contains("## 6. 不做什么"));
        assertTrue(result.contains("## 7. 当前备注"));
    }

    @Test
    void analysisRevisionWithLogicFixStillPreservesOtherSections() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.ANALYSIS);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.ANALYSIS) {
                    return """
                            # 需求分析与调研

                            ## 2. 目标与成功标准

                            ### 2.1 业务目标
                            统一 6x6 与 9x9 规则定义，并保证完成一题立即切下一题。

                            ### 2.2 用户价值
                            提供连续、低中断的数独体验。

                            ### 2.3 成功标准
                            1. 6x6 与 9x9 都可正确生成、游玩与切题。
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = new StageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace)
        );

        UUID runId = UUID.randomUUID();
        Path analysisPath = artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                旧背景。

                ## 2. 目标与成功标准
                旧目标。

                ## 3. 关键约束
                旧约束。

                ## 4. 初步调研与假设
                旧调研。

                ## 5. 边界与非目标
                旧边界。

                ## 6. 风险与待确认问题
                旧风险。
                """);

        RunRecord runRecord = analysisRunRecord(runId, analysisPath);
        String result = composer.compose(
                tempDir,
                runRecord,
                StageType.ANALYSIS,
                "需求基本完整，但核心参数存在逻辑矛盾且存在关键信息缺失。请统一 6x6 与 9x9 规格定义。"
        );

        assertTrue(result.contains("## 1. 背景与问题定义"));
        assertTrue(result.contains("## 3. 关键约束"));
        assertTrue(result.contains("## 4. 初步调研与假设"));
        assertTrue(result.contains("## 5. 边界与非目标"));
        assertTrue(result.contains("## 6. 风险与待确认问题"));
        assertTrue(result.contains("统一 6x6 与 9x9 规则定义"));
    }

    @Test
    void designPromptDoesNotInventPerformanceRequirementsWhenUpstreamDoesNotAskForIt() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        AtomicReference<String> capturedUserPrompt = new AtomicReference<>("");
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.DESIGN);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.DESIGN) {
                    capturedUserPrompt.set(userPrompt);
                    return """
                            # 技术方案设计

                            ## 1. 技术目标
                            内容

                            ## 2. 系统边界与模块划分
                            内容

                            ## 3. 核心数据模型
                            内容

                            ## 4. 关键流程
                            内容

                            ## 5. 接口、页面或命令设计
                            内容

                            ## 6. 测试与验证策略
                            内容

                            ## 7. 风险与取舍
                            内容

                            ## 8. 当前备注
                            内容
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = new StageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                new ImplementationExecutor(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace)
        );

        UUID runId = UUID.randomUUID();
        Path prdPath = artifactStore.writeArtifact(tempDir, runId, StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                做一个纯网页版数独。

                ## 2. 目标用户与使用场景
                面向日常休闲用户。

                ## 3. 功能范围
                支持 6x6/9x9、回退、标记和连续下一题。

                ## 4. 非功能要求
                界面清晰、易用、纯静态部署。

                ## 5. 验收标准
                功能完整可用。

                ## 6. 不做什么
                不做联网功能。
                """);

        RunRecord runRecord = designRunRecord(runId, prdPath);
        composer.compose(tempDir, runRecord, StageType.DESIGN, "");

        assertTrue(capturedUserPrompt.get().contains("如果上游需求没有明确性能目标，不要自行发明量化性能指标"));
    }

    private RunRecord runRecord(UUID runId, Path prdPath) {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(StageType.ANALYSIS, new StageExecution(
                StageType.ANALYSIS,
                StageStatus.APPROVED,
                1,
                tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("analysis.md").toString(),
                ReviewDecision.APPROVED,
                "ok",
                ""
        ));
        states.put(StageType.PRD, new StageExecution(
                StageType.PRD,
                StageStatus.NEEDS_REVISION,
                4,
                prdPath.toString(),
                ReviewDecision.REVISION_REQUIRED,
                "缺少章节",
                "请补齐以下章节后重试：## 5. 验收标准、## 6. 不做什么"
        ));
        return new RunRecord(
                runId,
                tempDir,
                "实现一个数独填空游戏",
                "需要纯网页版、要精致、卖像好",
                new RunConfig(Map.of(), 5),
                StageType.PRD,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }

    private RunRecord analysisRunRecord(UUID runId, Path analysisPath) {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(StageType.ANALYSIS, new StageExecution(
                StageType.ANALYSIS,
                StageStatus.NEEDS_REVISION,
                2,
                analysisPath.toString(),
                ReviewDecision.REVISION_REQUIRED,
                "存在逻辑矛盾",
                "请统一 6x6 与 9x9 规格定义"
        ));
        return new RunRecord(
                runId,
                tempDir,
                "实现一个数独填空游戏",
                "需要纯网页版、要精致、卖像好",
                new RunConfig(Map.of(), 5),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }

    private RunRecord designRunRecord(UUID runId, Path prdPath) {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(StageType.PRD, new StageExecution(
                StageType.PRD,
                StageStatus.APPROVED,
                1,
                prdPath.toString(),
                ReviewDecision.APPROVED,
                "ok",
                ""
        ));
        return new RunRecord(
                runId,
                tempDir,
                "实现一个数独填空游戏",
                "需要纯网页版、要精致、卖像好",
                new RunConfig(Map.of(), 5),
                StageType.DESIGN,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }
}
