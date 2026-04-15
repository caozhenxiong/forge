package devflow.agent.artifact;

import devflow.agent.executor.llm.ChatCapableLlmProvider;
import devflow.agent.executor.llm.LlmChatRequest;
import devflow.agent.executor.llm.LlmChatResponse;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.LlmToolCall;
import devflow.agent.executor.llm.ModelRole;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ProductContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.executor.ImplementationExecutor;
import devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewSemantics;
import devflow.agent.review.StructuredReviewResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageArtifactComposerTests {

    @TempDir
    Path tempDir;

    private StageArtifactComposer newStageArtifactComposer(
            ArtifactTemplateFactory artifactTemplateFactory,
            FileArtifactStore artifactStore,
            LlmProvider provider,
            ImplementationExecutor implementationExecutor,
            TestExecutor testExecutor,
            WorkspaceSnapshotStore snapshotStore,
            ContractExtractor contractExtractor
    ) {
        DocumentDraftAssembler draftAssembler = new DocumentDraftAssembler();
        DocumentStageIntake documentStageIntake = new DocumentStageIntake(
                artifactTemplateFactory,
                artifactStore,
                contractExtractor,
                new devflow.agent.i18n.LanguagePolicy(),
                draftAssembler
        );
        DocumentPromptAssembler promptAssembler = new DocumentPromptAssembler(
                new devflow.agent.prompt.PromptTemplateCatalog(),
                draftAssembler
        );
        return new StageArtifactComposer(
                artifactStore,
                provider,
                implementationExecutor,
                testExecutor,
                snapshotStore,
                contractExtractor,
                new DocumentStageComposer(
                        new DocumentCompositionTemplate(
                                contractExtractor,
                                documentStageIntake,
                                draftAssembler,
                                new DocumentStagePostProcessor(contractExtractor, draftAssembler),
                                new DocumentGenerationSupport(provider)
                        ),
                        new AnalysisDocumentComposition(contractExtractor, promptAssembler),
                        new PrdDocumentComposition(contractExtractor, documentStageIntake, promptAssembler),
                        new DesignDocumentComposition(contractExtractor, documentStageIntake, promptAssembler)
                ),
                new devflow.agent.i18n.LanguagePolicy(),
                new ImplementationStateArtifactSupport()
        );
    }

    @Test
    void prdRevisionCanFillOnlyMissingSectionsAndMergeBack() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
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

                ## 7. Current Notes
                旧备注
                """);

        RunRecord runRecord = runRecord(runId, prdPath);
        String result = composer.compose(
                tempDir,
                runRecord,
                StageType.PRD,
                ExecutionDirectiveProtocol.renderBlock(new ExecutionDirectivePayload(
                        null,
                        null,
                        List.of(),
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(5, 6),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null
                ))
        );

        assertTrue(result.contains("## 1. 产品目标"));
        assertTrue(result.contains("## 5. 验收标准"));
        assertTrue(result.contains("## 6. 不做什么"));
        assertFalse(result.contains("## 7. Current Notes"));
    }

    @Test
    void analysisRevisionWithLogicFixStillPreservesOtherSections() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
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
        LlmProvider provider = new StructuredTestLlmProvider() {
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

                            ## 8. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: true
                            - runtime.acceptanceSignals: page-opens, puzzle-renders

                            ## 9. Current Notes
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
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

        RunRecord runRecord = designRunRecordForSudoku(runId, prdPath);
        composer.compose(tempDir, runRecord, StageType.DESIGN, "");

        assertTrue(capturedUserPrompt.get().contains("validation.performanceMeasurementRequired"));
        assertTrue(capturedUserPrompt.get().contains("## 8. Contract Metadata"));
        assertTrue(capturedUserPrompt.get().contains("runtime.entryRequired"));
        assertTrue(capturedUserPrompt.get().contains("runtime.entryKind"));
    }

    @Test
    void designPromptTreatsImplementationOrganizationAsLowAuthorityReference() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        AtomicReference<String> capturedUserPrompt = new AtomicReference<>("");
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.DESIGN);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
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

                        ## 8. Contract Metadata
                        - runtime.entryRequired: true
                        - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                        - runtime.launchRequired: true
                        - runtime.surfaceRequired: true
                        - runtime.acceptanceSignals: page-opens

                        ## 9. Source Metadata
                        - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块
                        - hard.upstreamFacts: 需要纯网页版、可直接打开运行
                        - soft.inferences: (none)
                        - soft.designDecisions: (none)
                        - soft.recommendations: (none)
                        - open.questions: (none)
                        """;
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path prdPath = artifactStore.writeArtifact(tempDir, runId, StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                内容

                ## 2. 目标用户与使用场景
                内容

                ## 3. 功能范围
                内容

                ## 4. 非功能要求
                内容

                ## 5. 验收标准
                内容

                ## 6. 不做什么
                内容

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens
                """);

        composer.compose(tempDir, designRunRecordForSudoku(runId, prdPath), StageType.DESIGN, "");

        assertTrue(capturedUserPrompt.get().contains("上游文档中的技术实现倾向、文件组织暗示和资源组织偏好仅作为低权重参考"));
        assertTrue(capturedUserPrompt.get().contains("优先保留可替换性与模块边界"));
        assertTrue(capturedUserPrompt.get().contains("如果某个具体细节缺少来源支撑"));
        assertTrue(capturedUserPrompt.get().contains("都必须同步写入 Source Metadata")
                || capturedUserPrompt.get().contains("must also be mirrored into the corresponding"));
    }

    @Test
    void prdPromptClarifiesDirectLaunchDoesNotRequireSingleHtmlFile() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        AtomicReference<String> capturedUserPrompt = new AtomicReference<>("");
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.PRD);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.PRD) {
                    capturedUserPrompt.set(userPrompt);
                    return """
                            # Product Requirements Document

                            ## 1. Product Goals
                            Content

                            ## 2. Target Users And Scenarios
                            Content

                            ## 3. Scope
                            Content

                            ## 4. Non-Functional Requirements
                            Content

                            ## 5. Acceptance Criteria
                            Content

                            ## 6. Out Of Scope
                            Content

                            ## 7. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: true
                            - runtime.acceptanceSignals: page-opens

                            ## 8. Current Notes
                            Content
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                需要一个可直接打开运行的纯网页版俄罗斯方块。
                """);

        RunRecord runRecord = runRecord(runId, tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("prd.md"));
        composer.compose(tempDir, runRecord, StageType.PRD, "保持纯网页版、可直接打开运行");

        assertTrue(capturedUserPrompt.get().contains("默认使用 runtime.entryPackagingMode=entry-with-local-dependencies"));
        assertTrue(capturedUserPrompt.get().contains("不要在用户未明确提出时额外收紧为 self-contained-entry"));
        assertTrue(capturedUserPrompt.get().contains("未经来源支撑的实现细节只能作为建议、设计选择或待确认问题"));
    }

    @Test
    void prdContractMetadataIsStabilizedToNormalizedExecutionContract() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.PRD);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                return """
                        # Product Requirements Document

                        ## 1. Product Goals
                        Content

                        ## 2. Target Users And Scenarios
                        Content

                        ## 3. Scope
                        Content

                        ## 4. Non-Functional Requirements
                        Content

                        ## 5. Acceptance Criteria
                        Content

                        ## 6. Out Of Scope
                        Content

                        ## 7. Contract Metadata
                        - runtime.entryRequired: true
                        - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                        - runtime.launchRequired: false
                        - runtime.surfaceRequired: true
                        - runtime.acceptanceSignals: page-opens, input-works

                        ## 8. Source Metadata
                        - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块
                        - hard.upstreamFacts: 需要纯网页版、可直接打开运行
                        - soft.inferences: (none)
                        - soft.designDecisions: (none)
                        - soft.recommendations: (none)
                        - open.questions: (none)

                        ## 9. Current Notes
                        Content
                        """;
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path analysisPath = artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                内容

                ## 2. 目标与成功标准
                内容

                ## 3. 关键约束
                内容

                ## 4. 初步调研与假设
                内容

                ## 5. 边界与非目标
                内容

                ## 6. 风险与待确认问题
                内容

                ## 7. Source Metadata
                - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块
                - hard.upstreamFacts: 需要纯网页版、可直接打开运行
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """);

        String result = composer.compose(tempDir, prdRunRecord(runId, analysisPath), StageType.PRD, "");

        assertTrue(result.contains("- runtime.entryRequired: true"));
        assertTrue(result.contains("- runtime.entryKind: html-entry"));
        assertTrue(result.contains("- runtime.launchRequired: true"));
        assertTrue(result.contains("- runtime.surfaceRequired: true"));
        assertTrue(result.contains("- runtime.acceptanceSignals: page-opens, input-works, runtime-surface-renders"));
    }

    @Test
    void analysisKeepsAuthoredOpenQuestionBodyContent() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return """
                        # 需求分析与调研

                        ## 1. 背景与问题定义
                        内容

                        ## 2. 目标与成功标准
                        内容

                        ## 3. 关键约束
                        - 待确认问题：项目需在单一 HTML 文件中完成
                        - 待确认问题：使用纯网页技术（HTML、CSS、JavaScript）
                        - 不需要联网功能或数据持久化

                        ## 4. 初步调研与假设
                        内容

                        ## 5. 边界与非目标
                        内容

                        ## 6. 风险与待确认问题
                        内容

                        ## 7. Source Metadata
                        - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块, 需要纯网页版、可直接打开运行、像素风
                        - hard.upstreamFacts: (none)
                        - soft.inferences: (none)
                        - soft.designDecisions: (none)
                        - soft.recommendations: (none)
                        - open.questions: (none)
                        """;
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                return generate(systemPrompt, userPrompt, options);
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path placeholderAnalysisPath = tempDir.resolve("placeholder-analysis.md");
        RunRecord runRecord = analysisRunRecord(runId, placeholderAnalysisPath);
        String result = composer.compose(tempDir, runRecord, StageType.ANALYSIS, "");

        assertTrue(result.contains("待确认问题：项目需在单一 HTML 文件中完成"));
        assertTrue(result.contains("待确认问题：使用纯网页技术（HTML、CSS、JavaScript）"));
        assertTrue(result.contains("不需要联网功能或数据持久化"));
    }

    @Test
    void prdComposeRoutesLowAuthorityBodyItemsIntoSourceMetadataAndKeepsOptionalSectionSettledOnly() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ContractExtractor contractExtractor = new ContractExtractor();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return """
                        # 产品需求文档

                        ## 1. 产品目标
                        - 实现一个可在网页端运行的俄罗斯方块

                        ## 2. 目标用户与使用场景
                        - 用户打开页面后即可开始游玩

                        ## 3. 功能范围

                        ### 3.1 核心功能
                        - 支持开始、暂停、重新开始

                        ### 3.2 可选增强
                        - 推断：支持移动端触摸操作
                        - 建议：添加简单的音效反馈
                        - 支持不同难度等级设置

                        ### 3.3 异常与边界场景
                        - 游戏结束时应给出明确反馈

                        ## 4. 非功能要求

                        ### 4.1 性能
                        - 游戏运行流畅，无明显卡顿

                        ### 4.2 可用性与交互
                        - 设计选择：默认自动开始

                        ### 4.3 兼容性与部署约束
                        - 可直接通过浏览器打开运行

                        ## 5. 验收标准

                        ### 5.1 功能验收
                        - [ ] 页面可打开并能开始游戏

                        ### 5.2 质量验收
                        - [ ] 交互自然

                        ## 6. 不做什么

                        ### 6.1 本轮不包含
                        - 不做联网对战

                        ### 6.2 后续可扩展方向
                        - 待确认问题：是否需要首版支持触屏手势控制？

                        ## 7. Contract Metadata
                        - runtime.entryRequired: true
                        - runtime.entryKind: html-entry
                        - runtime.entryPackagingMode: entry-with-local-dependencies
                        - runtime.runtimeOwnershipMode: not-applicable
                        - runtime.launchRequired: true
                        - runtime.surfaceRequired: true
                        - runtime.acceptanceSignals: page-opens, input-works

                        ## 8. Source Metadata
                        - hard.userRequirements: 实现一个俄罗斯方块网页游戏, 做成精致的网页版
                        - hard.upstreamFacts: 实现一个俄罗斯方块网页游戏, 做成精致的网页版
                        - soft.inferences: (none)
                        - soft.designDecisions: (none)
                        - soft.recommendations: (none)
                        - open.questions: (none)
                        """;
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                return generate(systemPrompt, userPrompt, options);
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                contractExtractor
        );

        UUID runId = UUID.randomUUID();
        Path analysisPath = artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                内容

                ## 2. 目标与成功标准
                内容

                ## 3. 关键约束
                - 纯网页版、可直接打开运行

                ## 4. 初步调研与假设
                内容

                ## 5. 边界与非目标
                内容

                ## 6. 风险与待确认问题
                内容

                ## 7. Source Metadata
                - hard.userRequirements: 实现一个俄罗斯方块网页游戏, 做成精致的网页版
                - hard.upstreamFacts: (none)
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """);

        String result = composer.compose(tempDir, prdRunRecord(runId, analysisPath), StageType.PRD, "");
        ProductContract contract = contractExtractor.extractProductContract(result);
        ConstraintSourceMetadata metadata = contractExtractor.extractConstraintSourceMetadata(result);

        assertFalse(result.contains("推断：支持移动端触摸操作"), result);
        assertFalse(result.contains("建议：添加简单的音效反馈"), result);
        assertFalse(result.contains("设计选择：默认自动开始"), result);
        assertFalse(result.contains("待确认问题：是否需要首版支持触屏手势控制？"), result);
        assertTrue(result.contains("- soft.inferences: 支持移动端触摸操作"), result);
        assertTrue(result.contains("- soft.designDecisions: 默认自动开始"), result);
        assertTrue(result.contains("- soft.recommendations: 添加简单的音效反馈"), result);
        assertTrue(result.contains("- open.questions: 是否需要首版支持触屏手势控制"), result);
        assertTrue(contract.optionalCapabilities().contains("支持不同难度等级设置"));
        assertEquals(java.util.List.of("支持不同难度等级设置"), contract.optionalCapabilities());
        assertTrue(metadata.softInferences().contains("支持移动端触摸操作"));
        assertTrue(metadata.softRecommendations().contains("添加简单的音效反馈"));
        assertTrue(metadata.softDesignDecisions().contains("默认自动开始"));
        assertTrue(metadata.openQuestions().contains("是否需要首版支持触屏手势控制"));
    }

    @Test
    void analysisFullDraftKeepsTopLevelSectionsSeparatedAfterSanitization() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                return generate(systemPrompt, userPrompt, options);
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path placeholderAnalysisPath = tempDir.resolve("analysis-output.md");
        RunRecord runRecord = analysisRunRecord(runId, placeholderAnalysisPath);
        String result = composer.compose(tempDir, runRecord, StageType.ANALYSIS, "");

        assertTrue(result.contains("基础工作流。\n\n## 2. 目标与成功标准"), result);
        assertTrue(result.contains("基础状态机。\n\n## 4. 初步调研与假设"), result);
        assertFalse(result.contains("基础工作流。## 2. 目标与成功标准"), result);
        assertFalse(result.contains("基础状态机。## 4. 初步调研与假设"), result);
    }

    @Test
    void prdPromptKeepsAnalysisBodyContentInsteadOfGuessingLowAuthoritySemantics() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        AtomicReference<String> capturedUserPrompt = new AtomicReference<>("");
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                capturedUserPrompt.set(userPrompt);
                return """
                        # 产品需求文档

                        ## 1. 产品目标
                        内容

                        ## 2. 目标用户与使用场景
                        内容

                        ## 3. 功能范围
                        内容

                        ## 4. 非功能要求
                        内容

                        ## 5. 验收标准
                        内容

                        ## 6. 不做什么
                        内容

                        ## 7. Contract Metadata
                        - runtime.entryRequired: true
                        - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                        - runtime.launchRequired: true
                        - runtime.surfaceRequired: true
                        - runtime.acceptanceSignals: page-opens

                        ## 8. Source Metadata
                        - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块
                        - hard.upstreamFacts: 需要纯网页版、可直接打开运行
                        - soft.inferences: (none)
                        - soft.designDecisions: (none)
                        - soft.recommendations: (none)
                        - open.questions: (none)
                        """;
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                return generate(systemPrompt, userPrompt, options);
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path analysisPath = artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                内容

                ## 2. 目标与成功标准
                内容

                ## 3. 关键约束
                - 待确认问题：项目需在单一 HTML 文件中完成
                - 待确认问题：使用纯网页技术（HTML、CSS、JavaScript）
                - 不需要联网功能或数据持久化

                ## 4. 初步调研与假设
                内容

                ## 5. 边界与非目标
                内容

                ## 6. 风险与待确认问题
                内容

                ## 7. Source Metadata
                - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块, 需要纯网页版、可直接打开运行、像素风
                - hard.upstreamFacts: (none)
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """);

        composer.compose(tempDir, prdRunRecord(runId, analysisPath), StageType.PRD, "");

        assertTrue(capturedUserPrompt.get().contains("单一 HTML 文件"));
        assertTrue(capturedUserPrompt.get().contains("HTML、CSS、JavaScript"));
        assertTrue(capturedUserPrompt.get().contains("不需要联网功能或数据持久化"));
    }

    @Test
    void documentPromptsDistinguishSourceAndConstraintLevels() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        AtomicReference<String> analysisPrompt = new AtomicReference<>("");
        AtomicReference<String> prdPrompt = new AtomicReference<>("");
        AtomicReference<String> designPrompt = new AtomicReference<>("");
        LlmProvider provider = new StructuredTestLlmProvider() {
            private int toolLoopTurn;

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.ANALYSIS) {
                    analysisPrompt.set(userPrompt);
                    return """
                            # 需求分析与调研

                            ## 1. 背景与问题定义
                            内容

                            ## 2. 目标与成功标准
                            内容

                            ## 3. 关键约束
                            内容

                            ## 4. 初步调研与假设
                            内容

                            ## 5. 边界与非目标
                            内容

                            ## 6. 风险与待确认问题
                            内容

                            ## 7. Current Notes
                            内容
                            """;
                }
                if (role == ModelRole.PRD) {
                    prdPrompt.set(userPrompt);
                    return """
                            # 产品需求文档

                            ## 1. 产品目标
                            内容

                            ## 2. 目标用户与使用场景
                            内容

                            ## 3. 功能范围
                            内容

                            ## 4. 非功能要求
                            内容

                            ## 5. 验收标准
                            内容

                            ## 6. 不做什么
                            内容

                            ## 7. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: true
                            - runtime.acceptanceSignals: page-opens

                            ## 8. Current Notes
                            内容
                            """;
                }
                if (role == ModelRole.DESIGN) {
                    designPrompt.set(userPrompt);
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

                            ## 8. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: true
                            - runtime.acceptanceSignals: page-opens

                            ## 9. Current Notes
                            内容
                            """;
                }
                return "";
            }

            @Override
            public LlmChatResponse chat(LlmChatRequest request) {
                if (toolLoopTurn++ == 0) {
                    return new LlmChatResponse(
                            "",
                            List.of(new LlmToolCall(
                                    "tool-1",
                                    "Write",
                                    Map.of(
                                            "file_path", tempDir.resolve("index.html").toString(),
                                            "content", """
                                                    <!DOCTYPE html>
                                                    <html lang="zh-CN">
                                                    <head><meta charset="UTF-8"><title>Tetris</title></head>
                                                    <body><h1>Tetris</h1></body>
                                                    </html>
                                                    """
                                    )
                            )),
                            null,
                            ""
                    );
                }
                return new LlmChatResponse("done", List.of(), null, "stop");
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path analysisPath = artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                内容
                """);
        RunRecord analysisRun = analysisRunRecord(runId, analysisPath);
        composer.compose(tempDir, analysisRun, StageType.ANALYSIS, "");
        RunRecord prdRun = runRecord(runId, tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("prd.md"));
        composer.compose(tempDir, prdRun, StageType.PRD, "");
        artifactStore.writeArtifact(tempDir, runId, StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                内容
                """);
        RunRecord designRun = designRunRecord(runId, tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("prd.md"));
        composer.compose(tempDir, designRun, StageType.DESIGN, "");

        assertTrue(analysisPrompt.get().contains("用户明确要求 / 上游事实 / 推断 / 设计选择 / 建议 / 待确认问题"));
        assertTrue(prdPrompt.get().contains("用户明确要求 / 上游事实 / 推断 / 设计选择 / 建议 / 待确认问题"));
        assertTrue(designPrompt.get().contains("用户明确要求 / 上游事实 / 推断 / 设计选择 / 建议 / 待确认问题"));
        assertTrue(designPrompt.get().contains("上游文档中的技术实现倾向、文件组织暗示和资源组织偏好仅作为低权重参考"));
        assertTrue(analysisPrompt.get().contains("使用明确标签"));
        assertTrue(prdPrompt.get().contains("只写入 Source Metadata"));
        assertTrue(designPrompt.get().contains("使用明确标签"));
        assertTrue(analysisPrompt.get().contains("都必须能在 hard.userRequirements 或 hard.upstreamFacts 中找到来源支撑"));
        assertTrue(prdPrompt.get().contains("必须有 hard.* 来源支撑"));
        assertTrue(designPrompt.get().contains("绑定部分只能来自 Execution Contract 与 hard.*"));
    }

    @Test
    void codeReviewPromptIncludesStructuredContracts() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        AtomicReference<String> capturedUserPrompt = new AtomicReference<>("");
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.CODE_REVIEW);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.CODE_REVIEW) {
                    capturedUserPrompt.set(userPrompt);
                    return approvedReviewArtifact("ok");
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        ContractExtractor contractExtractor = new ContractExtractor();
        String prd = """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可玩的网页版俄罗斯方块

                ## 2. 目标用户与使用场景
                - 用户可以直接打开页面开始游戏

                ## 3. 功能范围
                - 支持开始、暂停、重开和得分显示

                ## 4. 非功能要求
                - 纯网页版，无外部依赖

                ## 5. 验收标准
                - 页面可启动且核心操作可用

                ## 6. 不做什么
                - 不做联网排行榜

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-surface-renders
                """;
        Path prdPath = artifactStore.writeArtifact(
                tempDir,
                runId,
                StageType.PRD,
                prd + "\n\n" + StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.PRODUCT_CONTRACT,
                        contractExtractor.projectProductContractFromPrd(prd)
                )
        );
        Path designPath = artifactStore.writeArtifact(tempDir, runId, StageType.DESIGN, """
                # 技术方案设计

                ## 1. 技术目标
                - 采用静态 HTML + JS 交付

                ## 2. 系统边界与模块划分
                - UI 与核心逻辑分离

                ## 3. 核心数据模型
                - 棋盘、当前方块、分数、运行状态

                ## 4. 关键流程
                - 启动、下落、碰撞、锁定、消行

                ## 5. 接口、页面或命令设计
                - 页面必须存在启动控件和游戏区域

                ## 6. 测试与验证策略
                - 通过浏览器 smoke 和功能 testcase 验证

                ## 7. 风险与取舍
                - 优先保证可玩性，再做视觉 polish

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-surface-renders
                """);
        Path implementationPath = artifactStore.writeArtifact(tempDir, runId, StageType.IMPLEMENTATION, """
                # 实现报告

                已实现基础页面与游戏逻辑。
                """);
        artifactStore.writeAuxiliaryArtifact(
                tempDir,
                runId,
                AuxiliaryArtifactNames.IMPLEMENTATION_STATE,
                renderImplementationState("已实现基础页面与游戏逻辑。", true, true)
        );

        RunRecord runRecord = codeReviewRunRecord(runId, prdPath, designPath, implementationPath);
        composer.compose(tempDir, runRecord, StageType.CODE_REVIEW, "请审阅这轮实现");

        assertTrue(capturedUserPrompt.get().contains("产品与设计约束（结构化 contract）"));
        assertTrue(capturedUserPrompt.get().contains("## 产品契约") || capturedUserPrompt.get().contains("## Product Contract"));
        assertTrue(capturedUserPrompt.get().contains("## 设计契约") || capturedUserPrompt.get().contains("## Design Contract"));
        assertTrue(capturedUserPrompt.get().contains("## 执行契约") || capturedUserPrompt.get().contains("## Execution Contract"));
        assertTrue(capturedUserPrompt.get().contains("entryKind: html-entry"));
        assertTrue(capturedUserPrompt.get().contains("可玩的网页版俄罗斯方块"));
        assertTrue(capturedUserPrompt.get().contains("页面必须存在启动控件和游戏区域"));
    }

    private String renderImplementationState(String summary, boolean planCompleted, boolean stageReady) {
        try {
            java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("subtasks", List.of());
            payload.put("reports", List.of());
            payload.put("events", List.of());
            payload.put("currentSubtaskTitle", "");
            payload.put("planCompleted", planCompleted);
            payload.put("stageReady", stageReady);
            payload.put("continuationMode", "MID_PLAN_CONTINUE");
            payload.put("continuationSummary", "");
            payload.put("continuationChangeRequest", "");
            payload.put("continuationEvidence", "");
            payload.put("continuationActionItems", "");
            payload.put("continuationOverrideChanges", List.of());
            payload.put("continuationPatchTarget", "NONE");
            payload.put("continuationReasonCode", "NONE");
            payload.put("incompleteSubtasks", List.of());
            return new ObjectMapper().writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void analysisSourceMetadataHardFieldsAreStabilizedFromAuthoritativeInputs() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.ANALYSIS);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.ANALYSIS) {
                    return """
                            # 需求分析与调研

                            ## 1. 背景与问题定义
                            内容

                            ## 2. 目标与成功标准
                            内容

                            ## 3. 关键约束
                            - 交付物必须为单个网页文件。

                            ## 4. 初步调研与假设
                            内容

                            ## 5. 边界与非目标
                            内容

                            ## 6. 风险与待确认问题
                            内容

                            ## 7. Source Metadata
                            - hard.userRequirements: 单文件实现
                            - hard.upstreamFacts: 必须单文件交付
                            - soft.inferences: 可以使用 Canvas
                            - soft.designDecisions: 使用单文件组织
                            - soft.recommendations: 保持像素风
                            - open.questions: 是否需要移动端支持

                            ## 8. Current Notes
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        RunRecord runRecord = new RunRecord(
                runId,
                tempDir,
                "实现一个可以直接在浏览器打开运行的俄罗斯方块网页",
                "需要纯网页版、生成的代码可直接运行、交付物必须可用。",
                new RunConfig(Map.of(), 5),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );

        String result = composer.compose(tempDir, runRecord, StageType.ANALYSIS, "");

        assertTrue(result.contains("- hard.userRequirements:"));
        assertTrue(result.contains("实现一个可以直接在浏览器打开运行的俄罗斯方块网页"));
        assertTrue(result.contains("需要纯网页版"));
        assertTrue(result.contains("生成的代码可直接运行"));
        assertTrue(result.contains("交付物必须可用"));
        assertTrue(result.contains("- hard.upstreamFacts: (none)"));
        assertFalse(result.contains("- hard.userRequirements: 单文件实现"));
        assertFalse(result.contains("- hard.upstreamFacts: 必须单文件交付"));
        assertTrue(result.contains("- soft.recommendations: 保持像素风"));
    }

    @Test
    void analysisKeepsAuthoredBindingImplementationClaimsInBody() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.ANALYSIS);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.ANALYSIS) {
                    return """
                            # 需求分析与调研

                            ## 1. 背景与问题定义
                            内容

                            ## 2. 目标与成功标准
                            内容

                            ## 3. 关键约束
                            - 必须使用纯网页技术实现（HTML、CSS、JavaScript）

                            ## 4. 初步调研与假设
                            内容

                            ## 5. 边界与非目标
                            内容

                            ## 6. 风险与待确认问题
                            内容

                            ## 7. Source Metadata
                            - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块, 需要纯网页版、可直接打开运行
                            - hard.upstreamFacts: (none)
                            - soft.inferences: (none)
                            - soft.designDecisions: (none)
                            - soft.recommendations: (none)
                            - open.questions: (none)

                            ## 8. Current Notes
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        RunRecord runRecord = new RunRecord(
                runId,
                tempDir,
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行",
                new RunConfig(Map.of(), 5),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );

        String result = composer.compose(tempDir, runRecord, StageType.ANALYSIS, "");

        assertTrue(result.contains("- 必须使用纯网页技术实现（HTML、CSS、JavaScript）"));
    }

    @Test
    void designKeepsAuthoredDeclarativeTechnicalStatementsInBody() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.DESIGN);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.DESIGN) {
                    return """
                            # 技术方案设计

                            ## 1. 技术目标
                            - **单文件可运行**：所有代码打包为一个 HTML 文件，可直接打开运行。

                            ## 2. 系统边界与模块划分
                            系统边界为单个 HTML 文件，包含 HTML 结构、CSS 样式和 JavaScript 逻辑，不依赖外部资源。

                            ## 3. 核心数据模型
                            - **游戏状态（GameState）**：包含 READY、PLAYING、PAUSED、GAME_OVER。

                            ## 4. 关键流程
                            - Design Choice: 游戏主循环启动后负责自动下落。

                            ## 5. 接口、页面或命令设计
                            - **HTML 结构**：包含游戏区域、得分区和预览区。

                            ## 6. 测试与验证策略
                            - Recommendation: 后续补充交互回归测试。

                            ## 7. 风险与取舍
                            内容

                            ## 8. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: true
                            - runtime.acceptanceSignals: page-opens

                            ## 9. Source Metadata
                            - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块, 需要纯网页版、可直接打开运行
                            - hard.upstreamFacts: (none)
                            - soft.inferences: (none)
                            - soft.designDecisions: (none)
                            - soft.recommendations: (none)
                            - open.questions: (none)
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path prdPath = artifactStore.writeArtifact(tempDir, runId, StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可玩的网页版俄罗斯方块

                ## 2. 目标用户与使用场景
                - 用户打开页面即可开始游戏

                ## 3. 功能范围
                - 支持开始、暂停、重开

                ## 4. 非功能要求
                - 纯静态部署

                ## 5. 验收标准
                - 页面可打开并能开始游戏

                ## 6. 不做什么
                - 不做联网功能

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens
                """);

        RunRecord runRecord = designRunRecord(runId, prdPath);
        String result = composer.compose(tempDir, runRecord, StageType.DESIGN, "");

        assertTrue(result.contains("**单文件可运行**：所有代码打包为一个 HTML 文件，可直接打开运行。"));
        assertTrue(result.contains("系统边界为单个 HTML 文件"));
        assertTrue(result.contains("Design Choice: 游戏主循环启动后负责自动下落。"));
    }

    @Test
    void prdDowngradesUnsourcedQuantitativeClaimsWithReadableEnglishLabel() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.PRD);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.PRD) {
                    return """
                            # Product Requirements

                            ## 1. Product Goals
                            Content

                            ## 2. Target Users And Scenarios
                            Content

                            ## 3. Scope
                            Content

                            ## 4. Non-Functional Requirements
                            - Users must get keyboard movement feedback within 100ms.

                            ## 5. Acceptance Criteria
                            Content

                            ## 6. Out Of Scope
                            Content

                            ## 7. Contract Metadata
                            - runtime.entryRequired: true
                            - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                            - runtime.launchRequired: true
                            - runtime.surfaceRequired: true
                            - runtime.acceptanceSignals: page-opens

                            ## 8. Source Metadata
                            - hard.userRequirements: build a browser game
                            - hard.upstreamFacts: runnable directly in browser
                            - soft.inferences: (none)
                            - soft.designDecisions: (none)
                            - soft.recommendations: (none)
                            - open.questions: (none)
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, new ObjectMapper(), testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path analysisPath = artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # Analysis

                ## 1. Problem
                Content

                ## 2. Goals
                Content

                ## 3. Constraints
                Content

                ## 4. Research
                Content

                ## 5. Boundaries
                Content

                ## 6. Risks
                Content
                """);
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(StageType.ANALYSIS, new StageExecution(StageType.ANALYSIS, StageStatus.APPROVED, 1, analysisPath.toString(), null, null, null));
        RunRecord runRecord = new RunRecord(
                runId,
                tempDir,
                "Build a browser game",
                "Must run directly in a browser.",
                new RunConfig(Map.of(), 5),
                StageType.PRD,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );

        String result = composer.compose(tempDir, runRecord, StageType.PRD, "");

        assertTrue(result.contains("100ms"));
    }

    @Test
    void implementationStageWritesSharedContextTaskPackagesAndWorkerResults() throws Exception {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return legacyPlanningResponse(userPrompt, """
                            {
                              "summary": "先建立最小页面骨架",
                              "subtasks": [
                                {
                                  "title": "建立页面骨架",
                                  "goal": "创建最小可运行入口",
                                  "deliveryMode": "SKELETON",
                                  "acceptanceCriteria": ["页面可打开", "存在标题"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建网页入口"
                                    }
                                  ]
                                }
                              ]
                            }
                    """);
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    return """
                            <!DOCTYPE html>
                            <html lang="zh-CN">
                            <head><meta charset="UTF-8"><title>Tetris</title></head>
                            <body><h1>Tetris</h1></body>
                            </html>
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态网页做基础资源检查即可。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "确认页面本地资源完整。",
                                  "required": true
                                }
                              ]
                            }
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, objectMapper);
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, objectMapper, testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                需要一个纯网页版俄罗斯方块。
                """);
        artifactStore.writeArtifact(tempDir, runId, StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个纯网页版俄罗斯方块

                ## 2. 目标用户与使用场景
                - 用户打开页面即可开始游戏

                ## 3. 功能范围
                - 支持开始、暂停、重开

                ## 4. 非功能要求
                - 纯静态部署

                ## 5. 验收标准
                - 页面可打开并能开始游戏

                ## 6. 不做什么
                - 不做联网功能
                """);
        Path designPath = artifactStore.writeArtifact(tempDir, runId, StageType.DESIGN, """
                # 技术方案设计

                ## 1. 技术目标
                - 维持纯静态网页运行形态

                ## 2. 系统边界与模块划分
                - 页面入口与游戏逻辑分离

                ## 3. 核心数据模型
                - 棋盘、方块、得分

                ## 4. 关键流程
                - 初始化、开始、暂停、重开

                ## 5. 接口、页面或命令设计
                - 页面暴露开始与暂停按钮

                ## 6. 测试与验证策略
                - 打开页面并检查主要控件

                ## 7. 风险与取舍
                - 优先功能正确，再做视觉增强
                """);

        RunRecord runRecord = implementationRunRecord(runId, designPath);
        String result = composer.compose(tempDir, runRecord, StageType.IMPLEMENTATION, "");

        assertTrue(result.contains("# 代码实现"));
        assertFalse(java.nio.file.Files.readString(
                tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("implementation_shared_context.md")
        ).isBlank());
        assertFalse(java.nio.file.Files.readString(
                tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("task_packages.md")
        ).isBlank());
        assertFalse(java.nio.file.Files.readString(
                tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("worker_results.md")
        ).isBlank());
        assertFalse(java.nio.file.Files.readString(
                tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("implementation_events.md")
        ).isBlank());
    }

    @Test
    void prdComposeKeepsValidationMetadataAndDropsUnsourcedThresholds() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.PRD);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role != ModelRole.PRD) {
                    return "";
                }
                return """
                        # 产品需求文档

                        ## 1. 产品目标
                        - 游戏可直接通过浏览器打开运行

                        ## 2. 目标用户与使用场景
                        - 用户打开网页即可开始游戏

                        ## 3. 功能范围
                        - 支持开始、暂停、重开

                        ## 4. 非功能要求

                        ### 4.1 性能
                        - 页面加载时间小于 2 秒
                        - 键盘响应延迟不超过 120 ms
                        - 支持主流浏览器运行

                        ### 4.2 可用性与交互
                        - 支持键盘方向键控制

                        ### 4.3 兼容性与部署约束
                        - 可直接通过浏览器打开运行

                        ## 5. 验收标准

                        ### 5.1 功能验收
                        - [ ] 点击开始后游戏可正常运行

                        ### 5.2 质量验收
                        - [ ] 页面加载时间不超过 2 秒
                        - [ ] 键盘响应延迟不超过 120 ms
                        - [ ] 游戏在主流浏览器中可正常运行

                        ## 6. 不做什么
                        - 联网功能

                        ## 7. Contract Metadata
                        - runtime.entryRequired: true
                        - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                        - runtime.launchRequired: true
                        - runtime.surfaceRequired: true
                        - runtime.acceptanceSignals: page-opens, input-works
                        - validation.performanceMeasurementRequired: true
                        - validation.pageLoadMaxMs: 2000
                        - validation.interactionMaxMs: (none)

                        ## 8. Source Metadata
                        - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块, 需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览
                        - hard.upstreamFacts: 实现一个可玩的网页版俄罗斯方块, 需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览
                        - soft.inferences: (none)
                        - soft.designDecisions: (none)
                        - soft.recommendations: (none)
                        - open.questions: (none)
                        """;
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

        TestExecutor testExecutor = devflow.agent.executor.testing.TestExecutorTestSupport.create(workspace, provider, objectMapper);
        StageArtifactComposer composer = newStageArtifactComposer(
                new ArtifactTemplateFactory(),
                artifactStore,
                provider,
                devflow.agent.executor.ImplementationExecutorTestSupport.create(provider, workspace, objectMapper, testExecutor),
                testExecutor,
                new WorkspaceSnapshotStore(runRepository, workspace),
                new ContractExtractor()
        );

        UUID runId = UUID.randomUUID();
        Path analysisPath = artifactStore.writeArtifact(tempDir, runId, StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                需要一个纯网页版俄罗斯方块。
                """);

        String result = composer.compose(tempDir, prdRunRecord(runId, analysisPath), StageType.PRD, "");
        ValidationMetadata validationMetadata = StructuredArtifactBlocks.readFirstJsonBlock(
                result,
                ArtifactBlockKind.VALIDATION_METADATA,
                ValidationMetadata.class
        );

        assertEquals(new ValidationMetadata(true, 2000, null), validationMetadata);
        assertTrue(result.contains("validation.pageLoadMaxMs: 2000"));
        assertTrue(result.contains("页面加载时间不超过 2 秒"));
        assertFalse(result.contains("键盘响应延迟不超过 120 ms"));
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

    private RunRecord implementationRunRecord(UUID runId, Path designPath) {
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
                StageStatus.APPROVED,
                1,
                tempDir.resolve(".devflow/runs").resolve(runId.toString()).resolve("prd.md").toString(),
                ReviewDecision.APPROVED,
                "ok",
                ""
        ));
        states.put(StageType.DESIGN, new StageExecution(
                StageType.DESIGN,
                StageStatus.APPROVED,
                1,
                designPath.toString(),
                ReviewDecision.APPROVED,
                "ok",
                ""
        ));
        return new RunRecord(
                runId,
                tempDir,
                "实现一个俄罗斯方块",
                "需要纯网页版",
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }

    private RunRecord designRunRecordForSudoku(UUID runId, Path prdPath) {
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
                StageStatus.APPROVED,
                1,
                prdPath.toString(),
                ReviewDecision.APPROVED,
                "ok",
                ""
        ));
        states.put(StageType.DESIGN, new StageExecution(
                StageType.DESIGN,
                StageStatus.RUNNING,
                1,
                null,
                null,
                null,
                null
        ));
        return new RunRecord(
                runId,
                tempDir,
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览",
                new RunConfig(Map.of(), 5),
                StageType.DESIGN,
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

    private RunRecord prdRunRecord(UUID runId, Path analysisPath) {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(StageType.ANALYSIS, new StageExecution(
                StageType.ANALYSIS,
                StageStatus.APPROVED,
                1,
                analysisPath.toString(),
                ReviewDecision.APPROVED,
                "ok",
                ""
        ));
        states.put(StageType.PRD, new StageExecution(
                StageType.PRD,
                StageStatus.RUNNING,
                1,
                null,
                null,
                null,
                null
        ));
        return new RunRecord(
                runId,
                tempDir,
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览",
                new RunConfig(Map.of(), 5),
                StageType.PRD,
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


    private String approvedReviewArtifact(String summary) {
        return StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.REVIEW_RESULT,
                new ReviewArtifactPayload(
                        ReviewDecision.APPROVED.name(),
                        FixMode.NONE.name(),
                        ImplementationPatchTarget.NONE.name(),
                        List.of(),
                        "PATCH_CURRENT_STAGE",
                        "NONE",
                        summary,
                        "",
                        "",
                        "",
                        false,
                        0,
                        null
                )
        ) + """

                ## Findings

                - 无阻塞问题
                """;
    }

    private RunRecord codeReviewRunRecord(UUID runId, Path prdPath, Path designPath, Path implementationPath) {
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
        states.put(StageType.DESIGN, new StageExecution(
                StageType.DESIGN,
                StageStatus.APPROVED,
                1,
                designPath.toString(),
                ReviewDecision.APPROVED,
                "ok",
                ""
        ));
        states.put(StageType.IMPLEMENTATION, new StageExecution(
                StageType.IMPLEMENTATION,
                StageStatus.APPROVED,
                1,
                implementationPath.toString(),
                ReviewDecision.APPROVED,
                "ok",
                ""
        ));
        return new RunRecord(
                runId,
                tempDir,
                "实现一个网页版俄罗斯方块",
                "需要纯网页版、可玩、支持开始暂停重开",
                new RunConfig(Map.of(), 5),
                StageType.CODE_REVIEW,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }

    private abstract static class StructuredTestLlmProvider extends devflow.agent.testsupport.RequestBackedLlmProvider implements ChatCapableLlmProvider {

        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            throw new UnsupportedOperationException("chat is not used in this test provider");
        }

        @Override
        public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
            return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
        }

        @Override
        public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
            return review(systemPrompt, candidateContent, options);
        }

        @Override
        public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options) {
            return new StructuredReviewResult(review(systemPrompt, candidateContent, options), ReviewSemantics.empty());
        }

        @Override
        public StructuredReviewResult reviewStructured(
                String systemPrompt,
                String candidateContent,
                Map<String, Object> options,
                ModelRole role
        ) {
            return new StructuredReviewResult(review(systemPrompt, candidateContent, options, role), ReviewSemantics.empty());
        }
    }

    private static String legacyPlanningResponse(String userPrompt, String legacyPlanJson) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode legacyPlan = objectMapper.readTree(legacyPlanJson);
            if (userPrompt != null && userPrompt.contains("上一轮 outline 反馈")) {
                ObjectNode outline = objectMapper.createObjectNode();
                outline.put("summary", legacyPlan.path("summary").asText(""));
                ArrayNode subtasks = outline.putArray("subtasks");
                ArrayNode legacySubtasks = legacyPlan.withArray("subtasks");
                for (int index = 0; index < legacySubtasks.size(); index++) {
                    com.fasterxml.jackson.databind.JsonNode legacySubtask = legacySubtasks.get(index);
                    ObjectNode subtask = subtasks.addObject();
                    subtask.put("id", "subtask-" + (index + 1));
                    subtask.put("title", legacySubtask.path("title").asText(""));
                    subtask.put("goal", legacySubtask.path("goal").asText(""));
                    ArrayNode targetPaths = subtask.putArray("targetPaths");
                    java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
                    for (com.fasterxml.jackson.databind.JsonNode change : legacySubtask.withArray("changes")) {
                        String path = change.path("path").asText("");
                        if (!path.isBlank()) {
                            paths.add(path);
                        }
                    }
                    for (String path : paths) {
                        targetPaths.add(path);
                    }
                    boolean touchesHtmlEntry = paths.stream().anyMatch(devflow.agent.util.ProjectPathSupport::isHtml);
                    String deliveryMode = legacySubtask.hasNonNull("deliveryMode")
                            ? legacySubtask.path("deliveryMode").asText("INCREMENTAL")
                            : (index == 0 && touchesHtmlEntry ? "SKELETON" : "INCREMENTAL");
                    boolean runnableMilestone = legacySubtask.has("runnableMilestone")
                            ? legacySubtask.path("runnableMilestone").asBoolean(false)
                            : (index == 0 && touchesHtmlEntry);
                    subtask.put("deliveryMode", deliveryMode);
                    subtask.put("runnableMilestone", runnableMilestone);
                    subtask.set("coverageRefs", copyArray(objectMapper, legacySubtask, "coverageRefs"));
                    subtask.set("ownedCapabilities", copyArray(objectMapper, legacySubtask, "ownedCapabilities"));
                    subtask.set("deferredCapabilities", copyArray(objectMapper, legacySubtask, "deferredCapabilities"));
                    subtask.set("acceptanceCriteria", copyArray(objectMapper, legacySubtask, "acceptanceCriteria"));
                }
                return objectMapper.writeValueAsString(outline);
            }
            String subtaskId = extractSubtaskId(userPrompt);
            if (subtaskId != null) {
                int index = Integer.parseInt(subtaskId.substring("subtask-".length())) - 1;
                com.fasterxml.jackson.databind.JsonNode legacySubtask = legacyPlan.withArray("subtasks").get(index);
                ObjectNode detail = objectMapper.createObjectNode();
                detail.put("subtaskId", subtaskId);
                detail.set("changes", copyArray(objectMapper, legacySubtask, "changes"));
                return objectMapper.writeValueAsString(detail);
            }
            return legacyPlanJson;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to adapt legacy planning fixture", exception);
        }
    }

    private static ArrayNode copyArray(
            ObjectMapper objectMapper,
            com.fasterxml.jackson.databind.JsonNode node,
            String fieldName
    ) {
        com.fasterxml.jackson.databind.JsonNode field = node == null ? null : node.get(fieldName);
        if (field == null || !field.isArray()) {
            return objectMapper.createArrayNode();
        }
        return field.deepCopy();
    }

    private static String extractSubtaskId(String userPrompt) {
        if (userPrompt == null) {
            return null;
        }
        int index = userPrompt.indexOf("- id: subtask-");
        if (index < 0) {
            return null;
        }
        int start = index + "- id: ".length();
        int end = userPrompt.indexOf('\n', start);
        return end < 0 ? userPrompt.substring(start).trim() : userPrompt.substring(start, end).trim();
    }
}
