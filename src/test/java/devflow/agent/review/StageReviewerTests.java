package devflow.agent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.executor.TestExecutor;
import devflow.agent.quality.CapabilityExpectation;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.quality.CapabilitySurface;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.CoverageLedgerEntry;
import devflow.agent.quality.CoverageLedgerStatus;
import devflow.agent.quality.QualityLedger;
import devflow.agent.quality.StructureRiskLevel;
import devflow.agent.quality.StructureRiskReport;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.nio.file.Path;
import java.time.Instant;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageReviewerTests {

    @TempDir
    Path tempDir;

    private StageReviewer newStageReviewer(LlmProvider provider) {
        return newStageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())
        );
    }

    private StageReviewer newStageReviewer(LlmProvider provider, TestExecutor testExecutor) {
        return newStageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                testExecutor
        );
    }

    private StageReviewer newStageReviewer(
            LlmProvider provider,
            WorkspaceSnapshotStore snapshotStore,
            TestExecutor testExecutor
    ) {
        return new StageReviewer(
                provider,
                snapshotStore,
                testExecutor,
                new devflow.agent.prompt.PromptTemplateCatalog(),
                new devflow.agent.i18n.LanguagePolicy(),
                new devflow.agent.loop.AgentTurnLoop()
        );
    }

    @Test
    void documentReviewFailsWhenOllamaReturnsEmptyContentAfterRetries() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                throw new IllegalStateException("Ollama returned empty content for model gemma4:26b after 3 attempts");
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                throw new IllegalStateException("Ollama returned empty content for model gemma4:26b after 3 attempts");
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                - 需要实现纯网页版数独。

                ## 2. 目标与成功标准
                - 支持 6x6 和 9x9。
                - 支持撤销和标记。

                ## 3. 关键约束
                - 不依赖后端。
                - 保持纯静态资源可运行。

                ## 4. 初步调研与假设
                - 同类产品通常提供难度切换和标记功能。

                ## 5. 边界与非目标
                - 本轮不做联网排行。

                ## 6. 风险与待确认问题
                - 需要确认 6x6 规格的宫格划分方式。
                """));
        assertEquals("Ollama returned empty content for model gemma4:26b after 3 attempts", exception.getMessage());
    }

    @Test
    void documentReviewRejectsWhenRequiredSectionsAreMissing() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标

                目标是做一个纯网页版数独。

                ## 2. 目标用户与使用场景

                面向碎片化时间用户。
                """);

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
    }

    @Test
    void documentReviewRejectsWhenSectionBodyIsEmptyShell() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                这是背景。

                ## 2. 目标与成功标准

                ## 3. 关键约束
                约束内容。

                ## 4. 初步调研与假设
                调研内容。

                ## 5. 边界与非目标
                边界内容。

                ## 6. 风险与待确认问题
                风险内容。
                """);

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
    }

    @Test
    void documentReviewPromptsCheckSourceAndConstraintPromotion() {
        AtomicReference<String> capturedSystemPrompt = new AtomicReference<>("");
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                capturedSystemPrompt.set(systemPrompt);
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                capturedSystemPrompt.set(systemPrompt);
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
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
                """);

        assertTrue(capturedSystemPrompt.get().contains("硬约束是否有明确来源"));
        assertTrue(capturedSystemPrompt.get().contains("推断、建议或技术倾向错误升级成了硬约束"));
        assertTrue(capturedSystemPrompt.get().contains("设计选择伪装成用户要求或既定事实"));
    }

    @Test
    void analysisDraftWithRequiredSectionsCanPassDeterministicGuard() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
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
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void analysisReviewDoesNotBlockOnOpenQuestionsThatAreAlreadyExplicitlyTracked() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "当前分析仍缺少若干关键细节。",
                        "请明确分数计算规则、预览方块显示位置和移动端支持策略。",
                        "文档中尚未给出这些细节的最终答案。",
                        "1. 补充分数规则。 2. 明确预览位置。 3. 说明移动端支持。"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), openQuestionSemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                用户希望做一个可直接运行的俄罗斯方块网页小游戏。

                ## 2. 目标与成功标准
                - 支持开始、暂停、重开。
                - 支持方向键控制。

                ## 3. 关键约束
                - 纯前端运行。
                - 需要可直接启动的入口。

                ## 4. 初步调研与假设
                - 推断：预览方块位置可能放在右侧信息栏。

                ## 5. 边界与非目标
                - 建议：移动端支持可作为后续增强。

                ## 6. 风险与待确认问题
                - 待确认问题：分数计算规则是否采用固定行数加分。
                - 待确认问题：预览方块显示位置是否固定在信息栏顶部。
                - 待确认问题：是否需要在首版支持移动端触控操作。

                ## 7. Source Metadata
                - hard.userRequirements: 纯前端运行, 需要可直接启动的入口
                - hard.upstreamFacts: (none)
                - soft.inferences: 预览方块可能放在右侧信息栏
                - soft.designDecisions: (none)
                - soft.recommendations: 移动端支持可作为后续增强
                - open.questions: 分数计算规则, 预览方块显示位置, 移动端支持策略
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void documentReviewRejectsInconsistentContractMetadata() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
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
                - runtime.launchRequired: false
                - runtime.surfaceRequired: false
                - runtime.acceptanceSignals: runtime-surface-renders
                """);

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.summary().contains("Contract Metadata"));
    }

    @Test
    void documentReviewDoesNotRejectExplicitlyLabeledDesignChoiceAsHardConstraint() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "技术方案 把缺少来源支撑的实现选择写成了硬约束：Design Choice: 用户点击“开始”按钮，游戏状态切换为 `running`",
                        "请把该实现选择改写为明确标注的设计选择/建议/待确认问题，或补充其来源：Design Choice: 用户点击“开始”按钮，游戏状态切换为 `running`"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), lowAuthoritySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
                # 技术方案设计

                ## 1. 技术目标
                内容

                ## 2. 系统边界与模块划分
                - Design Choice: 用户点击“开始”按钮，游戏状态切换为 `running`

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
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void documentReviewRelaxesNumberedLocalizedDesignChoiceFalsePositive() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "技术方案 把缺少来源支撑的实现选择写成了硬约束：3. 设计选择：`GameStateManager` 设置为运行状态",
                        "请把该实现选择改写为明确标注的设计选择/建议/待确认问题，或补充其来自用户要求/上游事实的来源：3. 设计选择：`GameStateManager` 设置为运行状态"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), lowAuthoritySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
                # 技术方案设计

                ## 1. 技术目标
                内容

                ## 2. 系统边界与模块划分
                内容

                ## 3. 核心数据模型
                内容

                ## 4. 关键流程
                1. 用户打开页面
                2. 点击开始按钮
                3. 设计选择：`GameStateManager` 设置为运行状态

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
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void documentReviewRelaxesNumberedEnglishDesignChoiceFalsePositive() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "技术方案 把缺少来源支撑的实现选择写成了硬约束：2. Design Choice: System initializes game state as `READY`",
                        "请把该实现选择改写为明确标注的设计选择/建议/待确认问题，或补充其来自用户要求/上游事实的来源：2. Design Choice: System initializes game state as `READY`"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), lowAuthoritySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
                # Technical Design

                ## 1. Technical Goals
                Content

                ## 2. System Boundaries And Module Split
                Content

                ## 3. Core Data Model
                Content

                ## 4. Key Flows
                1. User opens the page
                2. Design Choice: System initializes game state as `READY`
                3. User clicks the start button

                ## 5. Interface, Page, Or Command Design
                Content

                ## 6. Test And Validation Strategy
                Content

                ## 7. Risks And Tradeoffs
                Content

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void approvedDocumentReviewDoesNotCarryImplementationActionItemsForward() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.APPROVED,
                        FixMode.NONE,
                        "需求分析完整清晰，边界明确，可执行。",
                        "",
                        "问题定义清楚，目标与约束覆盖充分。",
                        "创建游戏核心模块并补齐渲染逻辑"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), unsupportedHardeningSemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
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
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals("", result.actionItems());
    }

    @Test
    void documentReviewNoLongerGuessesUnsourcedBindingImplementationConstraintFromBodyText() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
                # 技术方案设计

                ## 1. 技术目标
                - 目标是做一个可直接打开运行的网页版游戏。

                ## 2. 系统边界与模块划分
                - 必须使用 HTML5 Canvas 渲染游戏画面。
                - 不使用任何构建工具或打包工具。

                ## 3. 核心数据模型
                - 棋盘、当前方块、分数。

                ## 4. 关键流程
                - 启动、移动、旋转、锁定、消行。

                ## 5. 接口、页面或命令设计
                - 页面提供开始按钮和游戏区域。

                ## 6. 测试与验证策略
                - 通过浏览器自动化验证。

                ## 7. 风险与取舍
                - 风险可控。

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens

                ## 9. Source Metadata
                - hard.userRequirements: implement a browser game
                - hard.upstreamFacts: runnable directly in browser, no backend dependency, no requirement for single-file implementation
                - soft.inferences: performant gameplay
                - soft.designDecisions: use HTML5 Canvas for rendering
                - soft.recommendations: consider requestAnimationFrame
                - open.questions: (none)
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void documentReviewNoLongerGuessesBindingImplementationConstraintFromGenericLaunchTerms() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
                # 技术方案设计

                ## 1. 技术目标
                - 目标是做一个可直接打开运行的网页版游戏。

                ## 2. 系统边界与模块划分
                - 游戏需以单个 HTML 文件形式交付，可直接打开运行。
                - 所有资源需内联或通过标准浏览器方式加载。

                ## 3. 核心数据模型
                - 棋盘、当前方块、分数。

                ## 4. 关键流程
                - 启动、移动、旋转、锁定、消行。

                ## 5. 接口、页面或命令设计
                - 页面提供开始按钮和游戏区域。

                ## 6. 测试与验证策略
                - 通过浏览器自动化验证。

                ## 7. 风险与取舍
                - 风险可控。

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens

                ## 9. Source Metadata
                - hard.userRequirements: implement a browser game
                - hard.upstreamFacts: runnable directly in browser, no backend dependency
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void documentReviewNoLongerGuessesDeclarativeTechnicalConstraintFromBodyPresentation() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
                # 技术方案设计

                ## 1. 技术目标
                - **单文件可运行**：所有代码打包为一个 HTML 文件，可直接打开运行。

                ## 2. 系统边界与模块划分
                系统边界为单个 HTML 文件，包含 HTML 结构、CSS 样式和 JavaScript 逻辑，不依赖外部资源。

                ## 3. 核心数据模型
                - 棋盘、当前方块、分数。

                ## 4. 关键流程
                - 启动、移动、旋转、锁定、消行。

                ## 5. 接口、页面或命令设计
                - 页面提供开始按钮和游戏区域。

                ## 6. 测试与验证策略
                - 通过浏览器自动化验证。

                ## 7. 风险与取舍
                - 风险可控。

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens

                ## 9. Source Metadata
                - hard.userRequirements: implement a browser game
                - hard.upstreamFacts: runnable directly in browser, no backend dependency
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void documentReviewAllowsExplicitlyLabeledDesignChoices() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.DESIGN, """
                # 技术方案设计

                ## 1. 技术目标
                - 目标是做一个可直接打开运行的网页版游戏。

                ## 2. 系统边界与模块划分
                - 设计选择：使用 HTML5 Canvas 渲染游戏画面。
                - 设计选择：暂不引入构建工具，保持交付简单。

                ## 3. 核心数据模型
                - 棋盘、当前方块、分数。

                ## 4. 关键流程
                - 启动、移动、旋转、锁定、消行。

                ## 5. 接口、页面或命令设计
                - 页面提供开始按钮和游戏区域。

                ## 6. 测试与验证策略
                - 通过浏览器自动化验证。

                ## 7. 风险与取舍
                - 风险可控。

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens

                ## 9. Source Metadata
                - hard.userRequirements: implement a browser game
                - hard.upstreamFacts: runnable directly in browser, no backend dependency, no requirement for single-file implementation
                - soft.inferences: performant gameplay
                - soft.designDecisions: use HTML5 Canvas for rendering
                - soft.recommendations: consider requestAnimationFrame
                - open.questions: (none)
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
    }

    @Test
    void documentReviewAllowsExplicitlyLabeledQuantitativeRecommendations() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
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

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                内容

                ## 2. 目标用户与使用场景
                内容

                ## 3. 功能范围
                内容

                ## 4. 非功能要求
                - Recommendation: 用户操作应尽量保持流畅，建议后续设计阶段评估 100ms 以内的响应目标是否合理。

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
                - hard.userRequirements: implement a browser game
                - hard.upstreamFacts: runnable directly in browser
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
    }

    @Test
    void documentReviewIgnoresUnsupportedHardeningRequestsFromReviewer() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "需求定义模糊，关键约束和成功标准不明确",
                        "明确技术约束中的框架使用限制，补充成功标准的量化指标",
                        "缺少关键约束的明确说明，成功标准未量化，如'流畅性'需定义帧率要求",
                        ""
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), downstreamOnlySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                内容

                ## 2. 目标与成功标准
                - 游戏可直接在浏览器中打开并运行。
                - 支持开始、暂停、重开和方向键控制。

                ## 3. 关键约束
                - 待确认问题：使用纯网页技术实现。
                - 待确认问题：不允许使用外部依赖库或框架（除非明确允许）。

                ## 4. 初步调研与假设
                内容

                ## 5. 边界与非目标
                内容

                ## 6. 风险与待确认问题
                内容

                ## 7. Source Metadata
                - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块, 需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览
                - hard.upstreamFacts: (none)
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void analysisReviewIgnoresDownstreamDesignDetailsAsBlockingIssues() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REJECTED,
                        FixMode.REWORK,
                        "需求分析缺少唯一解验证机制、性能指标量化标准和页面原型细节。",
                        "补充算法实现细节、唯一解验证机制、500ms 性能指标和交互原型后再进入下一阶段。",
                        "当前文档没有展开唯一解验证和性能测试细节。",
                        "1. 补充回溯与唯一解验证机制。 2. 明确 500ms 指标。 3. 给出页面原型。"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), downstreamOnlySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                需要实现纯网页版数独，支持 6x6 和 9x9 两种规格。

                ## 2. 目标与成功标准
                用户可以连续完成题目、撤销输入并使用标记模式。

                ## 3. 关键约束
                只能使用静态前端资源，不能依赖后端或构建工具。

                ## 4. 初步调研与假设
                目标用户偏好轻量、快速开始、可连续游玩的数独体验。

                ## 5. 边界与非目标
                本轮不做账号体系、排行榜和云端同步。

                ## 6. 风险与待确认问题
                需要在后续设计阶段确认数独引擎和测试策略。
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertTrue(result.summary().contains("DESIGN/TEST_CASE"));
    }

    @Test
    void analysisReviewIgnoresBusinessRulesAndLayoutSpecsAsBlockingIssues() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "需求定义不完整，缺少关键成功标准和实现细节。",
                        "明确游戏核心规则（如消行规则、得分计算）和界面布局规范。",
                        "未定义业务规则细节、计分公式以及界面尺寸规范。",
                        "补充规则细节，明确得分计算公式，定义布局和尺寸规范。"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), downstreamOnlySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                需要实现纯网页版俄罗斯方块，支持键盘控制和得分展示。

                ## 2. 目标与成功标准
                用户可以直接打开运行游戏，并完成开始、暂停、重开等核心操作。

                ## 3. 关键约束
                只能使用静态前端资源，不依赖后端或构建工具。

                ## 4. 初步调研与假设
                用户希望快速开始、交互流畅、界面具有像素风特征。

                ## 5. 边界与非目标
                本轮不做联网对战、排行榜和账户系统。

                ## 6. 风险与待确认问题
                详细玩法规则、得分公式和布局规格可在 PRD / DESIGN 阶段继续细化。
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertTrue(result.summary().contains("DESIGN/TEST_CASE"));
    }

    @Test
    void prdReviewIgnoresDownstreamTechnicalDetailsAsBlockingIssues() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "PRD 缺少唯一解验证机制和回溯算法细节说明。",
                        "补充唯一解验证机制、回溯算法和 benchmark 方案后再继续。",
                        "当前文档没有展开算法和技术细节。",
                        "1. 补充唯一解验证。2. 补充回溯算法。3. 补充 benchmark。"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), downstreamOnlySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                提供一个纯网页版数独体验，支持 6x6 和 9x9。

                ## 2. 目标用户与使用场景
                面向想要快速开始、连续解题的休闲用户。

                ## 3. 功能范围
                支持填数、标记、撤销、回退和完成后自动下一题。

                ## 4. 非功能要求
                页面响应清晰、操作反馈及时、纯静态资源可运行。

                ## 5. 验收标准
                用户可完成一题并自动进入下一题；错误输入有提示。

                ## 6. 不做什么
                本轮不做账号、排行和联网同步。

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, puzzle-renders
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertTrue(result.summary().contains("DESIGN/TEST_CASE"));
    }

    @Test
    void prdReviewIgnoresDeferredBusinessRuleFormulasAsBlockingIssues() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "功能边界不清晰，缺少关键实现细节。",
                        "明确积分计算规则、方块下落速度算法、行消除奖励机制。",
                        "文档中列出了待确认的计分公式、速度递增规则和奖励倍数。",
                        "补充积分公式、下落速度递增算法、消除奖励倍数定义。"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), downstreamOnlySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                提供一个可直接打开运行的网页版俄罗斯方块体验。

                ## 2. 目标用户与使用场景
                面向想要快速开始、键盘操作顺畅的休闲玩家。

                ## 3. 功能范围

                ### 3.1 核心功能
                - 支持开始、暂停、重新开始。
                - 支持左右移动、加速下落和旋转。
                - 提供得分显示和下一个方块预览。

                ### 3.2 辅助功能
                - 保持像素风视觉风格。

                ### 3.3 异常与边界场景
                - 积分计算规则未明确：待确认积分公式。
                - 方块下落速度递增规则未明确：待确认具体算法。
                - 行消除奖励机制未明确：待确认奖励倍数。

                ## 4. 非功能要求
                页面应可直接打开运行，交互反馈清晰。

                ## 5. 验收标准
                页面可启动，开始/暂停/重开和键盘控制可用。

                ## 6. 不做什么
                本轮不做联网对战和排行榜。

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, input-works
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertTrue(result.summary().contains("DESIGN/TEST_CASE"));
    }

    @Test
    void documentReviewRejectsUnsourcedQuantitativeHardConstraints() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
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
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), downstreamOnlySemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # Product Requirements Document

                ## 1. Product Goals
                - 提供一个可直接打开运行的网页版俄罗斯方块。

                ## 2. Target Users And Scenarios
                - 用户打开页面即可开始游戏。

                ## 3. Scope
                - 支持开始、暂停、重开、方向键控制、得分和预览。

                ## 4. Non-Functional Requirements
                - 游戏响应延迟不超过 100ms。
                - 页面加载时间不超过 2 秒。
                - 游戏帧率稳定在 30 FPS 以上。

                ## 5. Acceptance Criteria
                - 页面可直接打开并开始游戏。

                ## 6. Out Of Scope
                - 不做排行榜。

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, input-works

                ## 8. Source Metadata
                - hard.userRequirements: implement a web-based Tetris game, support start, pause, restart, keyboard control, score tracking, next piece preview, pixel-style UI
                - hard.upstreamFacts: pure web version, can be opened directly in browser, no backend required, no single-file requirement
                - soft.inferences: browser compatibility is important
                - soft.designDecisions: use DOM or Canvas for rendering
                - soft.recommendations: keep the UI responsive
                - open.questions: whether to support hard drop
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void documentReviewRejectsMalformedPrdStructure() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
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
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), performanceClaimWithoutMeasurementSemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                提供一个纯网页版数独体验。

                ## 2. 目标用户与使用场景
                面向休闲解谜用户。

                ## 3. 功能范围
                支持 6x6 与 9x9，支持填数、标记、撤销。

                ## 4.
                页面需要精致，提供清晰反馈与

                ## 5. 验收标准
                用户完成当前题目后自动进入下一题。

                ## 6. 不做什么
                本轮不做排行榜。

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, puzzle-renders
                """);

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.summary().contains("缺少规范章节")
                || result.summary().contains("结构完整性问题"));
    }

    @Test
    void documentReviewAllowsExtraWellFormedTopLevelSection() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
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
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), performanceClaimWithoutMeasurementSemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.ANALYSIS, """
                # 需求分析与调研

                ## 1. 背景与问题定义
                这是背景。

                ## 2. 目标与成功标准
                这是目标。

                ## 3. 关键约束
                这是约束。

                ## 4. 初步调研与假设
                这是调研。

                ## 5. 边界与非目标
                这是边界。

                ## 6. 风险与待确认问题
                这是风险。

                ## 7. Current Notes
                这是补充备注。
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void prdReviewIgnoresTrailingProcessNotesWhenMainSectionsAreComplete() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
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
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), performanceClaimWithoutMeasurementSemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # Product Requirements Document

                ## 1. Product Goals
                - 实现纯网页版俄罗斯方块。

                ## 2. Target Users And Scenarios
                - 用户可直接打开页面进入游戏。

                ## 3. Scope
                - 支持开始、暂停、重开、计分与下一个方块预览。

                ## 4. Non-Functional Requirements
                - 使用纯 HTML、CSS、JavaScript 实现。

                ## 5. Acceptance Criteria
                - 页面可打开并开始游戏。

                ## 6. Out Of Scope

                ### 6.1 Not Included In This Iteration
                - 多人联机
                - 排行榜

                ### 6.2 Future Extensions
                - 音效与背景音乐
                - 移动端触摸操作

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, input-works

                ## 8. Current Notes

                [FIX_MODE=PATCH]
                摘要：
                PRD第6章内容截断，需补充完整

                修改要求：
                补充## 6. Out Of Scope 章节完整内容

                [SUPERVISOR_GUIDANCE]
                [REQUIRED_EVIDENCE]
                - 补充后的PRD文件中## 6. 章节完整内容
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void prdReviewDoesNotTreatNormalFutureExtensionBulletsAsTruncatedTail() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
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
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), performanceClaimWithoutMeasurementSemantics());
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                - 实现纯网页版俄罗斯方块。

                ## 2. 目标用户与使用场景
                - 用户可直接打开页面进入游戏。

                ## 3. 功能范围
                - 支持开始、暂停、重开、计分与下一个方块预览。

                ## 4. 非功能要求
                - 游戏文件可直接运行。

                ## 5. 验收标准
                - 页面可打开并开始游戏。

                ## 6. 不做什么

                ### 6.1 本轮不包含
                - 多人联机
                - 排行榜

                ### 6.2 后续可扩展方向
                - 增加本地存储功能
                - 增加多语言支持

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, input-works
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void implementationReviewDefersUnsupportedPerformanceClaimToTestStage() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REJECTED,
                        FixMode.REWORK,
                        "数独算法存在性能问题，无法满足500ms验收标准",
                        "重构算法引擎，优化回溯和唯一解检测逻辑，提升执行效率",
                        "",
                        "",
                        ImplementationPatchTarget.NONE,
                        java.util.List.of(new devflow.agent.executor.FileChange(
                                "sudoku-engine.js",
                                devflow.agent.executor.ChangeAction.WRITE,
                                "补充性能测量与优化"
                        ))
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), performanceClaimWithoutMeasurementSemantics());
            }
        };

        TestExecutor testExecutor = new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()) {
            @Override
            public devflow.agent.executor.SelfCheckResult selfCheck(Path projectPath) {
                return new devflow.agent.executor.SelfCheckResult(true, "项目自测通过。", "未包含性能测量数据。");
            }
        };

        StageReviewer reviewer = newStageReviewer(provider, testExecutor);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertTrue(result.summary().contains("TEST 阶段"));
        assertEquals("", result.changeRequest());
        assertTrue(result.evidence().contains("耗时测量数据"));
        assertTrue(result.actionItems().contains("TEST 阶段"));
    }

    @Test
    void implementationReviewRequestsMeasurementWhenDesignRequiresIt() throws Exception {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REJECTED,
                        FixMode.REWORK,
                        "数独算法存在性能问题，无法满足500ms验收标准",
                        "重构算法引擎，优化回溯和唯一解检测逻辑，提升执行效率",
                        "",
                        "",
                        ImplementationPatchTarget.NONE,
                        java.util.List.of(new devflow.agent.executor.FileChange(
                                "sudoku-engine.js",
                                devflow.agent.executor.ChangeAction.WRITE,
                                "补充性能测量与优化"
                        ))
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), performanceClaimWithoutMeasurementSemantics());
            }
        };

        TestExecutor testExecutor = new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()) {
            @Override
            public devflow.agent.executor.SelfCheckResult selfCheck(Path projectPath) {
                return new devflow.agent.executor.SelfCheckResult(true, "项目自测通过。", "未包含性能测量数据。");
            }
        };

        Path designPath = tempDir.resolve("design.md");
        Files.writeString(designPath, """
                # 技术方案设计

                ## 8. Contract Metadata
                - validation.performanceMeasurementRequired: true
                - validation.pageLoadMaxMs: 500
                """);
        RunRecord runRecord = dummyRunWithDesign(designPath);

        StageReviewer reviewer = newStageReviewer(provider, testExecutor);

        ReviewResult result = reviewer.review(tempDir, runRecord, StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.summary().contains("技术方案已要求性能测量"));
        assertTrue(result.changeRequest().contains("DESIGN"));
    }

    @Test
    void implementationReviewDoesNotBlockOnGenericDesignValidationLanguage() throws Exception {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "存在性能风险，但缺少数据",
                        "请补性能测量",
                        "",
                        "",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return structured(review(systemPrompt, candidateContent, options), performanceClaimWithoutMeasurementSemantics());
            }
        };

        TestExecutor testExecutor = new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()) {
            @Override
            public devflow.agent.executor.SelfCheckResult selfCheck(Path projectPath) {
                return new devflow.agent.executor.SelfCheckResult(true, "项目自测通过。", "未包含性能测量数据。");
            }
        };

        Path designPath = tempDir.resolve("design-generic.md");
        Files.writeString(designPath, """
                # 技术方案设计

                ## 6. 测试与验证策略
                - 做功能测试
                - 做兼容性测试
                - 做基础性能测试验证加载体验
                """);
        RunRecord runRecord = dummyRunWithDesign(designPath);

        StageReviewer reviewer = newStageReviewer(provider, testExecutor);

        ReviewResult result = reviewer.review(tempDir, runRecord, StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertTrue(result.summary().contains("TEST 阶段"));
    }

    @Test
    void implementationReviewBackfillsEvidenceAndActionItemsWhenMissing() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "入口接线不完整",
                        "修复入口接线并重新验证",
                        "",
                        "",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        java.util.List.of(new devflow.agent.executor.FileChange(
                                "index.html",
                                devflow.agent.executor.ChangeAction.WRITE,
                                "修复入口接线"
                        ))
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }
        };

        TestExecutor testExecutor = new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()) {
            @Override
            public devflow.agent.executor.SelfCheckResult selfCheck(Path projectPath) {
                return new devflow.agent.executor.SelfCheckResult(true, "项目自测通过。", "基础自检通过。");
            }
        };

        StageReviewer reviewer = newStageReviewer(provider, testExecutor);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.evidence().contains("补充支持该结论的直接证据"));
        assertTrue(result.actionItems().contains("定位受影响文件和函数"));
    }

    @Test
    void implementationReviewIncludesRepairBriefAndAlignmentArtifacts() {
        AtomicReference<String> capturedCandidate = new AtomicReference<>("");
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public StructuredReviewResult reviewStructured(
                    String systemPrompt,
                    String candidateContent,
                    Map<String, Object> options,
                    ModelRole role
            ) {
                capturedCandidate.set(candidateContent);
                return structured(new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", ""), ReviewSemantics.empty());
            }
        };

        TestExecutor testExecutor = new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()) {
            @Override
            public devflow.agent.executor.SelfCheckResult selfCheck(Path projectPath) {
                return new devflow.agent.executor.SelfCheckResult(true, "项目自测通过。", "基础自检通过。");
            }
        };
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        RunRecord runRecord = dummyRun();
        artifactStore.writeAuxiliaryArtifact(tempDir, runRecord.runId(), AuxiliaryArtifactNames.REVIEWER_CONTEXT, "已有 reviewer 上下文");
        artifactStore.writeAuxiliaryArtifact(tempDir, runRecord.runId(), AuxiliaryArtifactNames.REPAIR_BRIEF, "必须优先修复入口接线");
        artifactStore.writeAuxiliaryArtifact(tempDir, runRecord.runId(), AuxiliaryArtifactNames.REPAIR_ALIGNMENT, "本轮修复已覆盖 must-fix-first 和 acceptance checks");

        StageReviewer reviewer = newStageReviewer(
                provider,
                new WorkspaceSnapshotStore(runRepository, new FileProjectWorkspace()),
                testExecutor
        );

        ReviewResult result = reviewer.review(tempDir, runRecord, StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertTrue(capturedCandidate.get().contains("## Reviewer Context"));
        assertTrue(capturedCandidate.get().contains("已有 reviewer 上下文"));
        assertTrue(capturedCandidate.get().contains("## Repair Brief"));
        assertTrue(capturedCandidate.get().contains("必须优先修复入口接线"));
        assertTrue(capturedCandidate.get().contains("## Repair Alignment"));
        assertTrue(capturedCandidate.get().contains("覆盖 must-fix-first"));
    }

    @Test
    void codeReviewApprovedWithStructuredBlockingMetadataIsDowngraded() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.CODE_REVIEW, """
                %s

                # 代码审阅
                """.formatted(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload(
                                "APPROVED",
                                "NONE",
                                ImplementationPatchTarget.NONE.name(),
                                java.util.List.of(),
                                "PATCH_CURRENT_STAGE",
                                "NONE",
                                "基础结构已经建立。",
                                "请补齐得分更新与方块移动/旋转行为。",
                                "当前开始游戏后分数不会变化，左右移动与旋转也未形成真实行为。",
                                "修复得分逻辑，并补齐移动与旋转接线。",
                                true,
                                2,
                                null
                        )
                )));

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.evidence().contains("分数不会变化"));
    }

    @Test
    void codeReviewApprovedWithStructuredBlockingEvidenceIsDowngraded() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.CODE_REVIEW, """
                %s
                """.formatted(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload(
                                "APPROVED",
                                "NONE",
                                ImplementationPatchTarget.NONE.name(),
                                java.util.List.of(),
                                "PATCH_CURRENT_STAGE",
                                "NONE",
                                "代码实现了基础俄罗斯方块游戏的HTML结构、CSS样式和初始游戏逻辑，符合设计与实现目标。",
                                "请补齐暂停恢复、移动旋转与消行逻辑。",
                                "pauseBtn 仅切换按钮文字，keydown 省略了移动与旋转主体逻辑，PIECES 未参与生成与消行。",
                                "修复暂停恢复，补齐移动旋转，并实现生成与消行。",
                                true,
                                3,
                                null
                        )
                )));

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.evidence().contains("pauseBtn"));
    }

    @Test
    void codeReviewApprovedWithBlockingEvidenceIsDowngraded() {
        LlmProvider provider = new StructuredTestLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return review(systemPrompt, candidateContent, options);
            }
        };

        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.CODE_REVIEW, """
                %s

                # 代码审阅
                """.formatted(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload(
                                "APPROVED",
                                "NONE",
                                ImplementationPatchTarget.NONE.name(),
                                java.util.List.of(),
                                "PATCH_CURRENT_STAGE",
                                "NONE",
                                "主流程已搭好。",
                                "",
                                "inputHandler.js 调用了 hardDrop，但当前实现缺少该方法，运行时会失败。",
                                "修复未实现的方法并补齐运行接线。",
                                true,
                                1,
                                null
                        )
                )));

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.evidence().contains("hardDrop"));
    }

    @Test
    void implementationReviewShortCircuitsOnFailedSelfCheck() {
        LlmProvider provider = new NoopReviewProvider();
        TestExecutor testExecutor = new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper()) {
            @Override
            public devflow.agent.executor.SelfCheckResult selfCheck(Path projectPath) {
                return new devflow.agent.executor.SelfCheckResult(false, "项目自测失败。", "存在语法错误。");
            }
        };
        StageReviewer reviewer = newStageReviewer(provider, testExecutor);

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertEquals("项目自测失败。", result.summary());
        assertEquals("存在语法错误。", result.changeRequest());
    }

    @Test
    void implementationReviewPatchesCurrentStageWhenRuntimeOwnershipViolatesApprovedContract() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <canvas id="game"></canvas>
                          <button id="startBtn">开始</button>
                          <script>
                            console.log('inline game logic');
                          </script>
                        </body>
                        </html>
                        """
        );
        Path designPath = tempDir.resolve("design.md");
        Files.writeString(
                designPath,
                """
                        # 技术方案设计

                        ## 8. Contract Metadata
                        - runtime.entryRequired: true
                        - runtime.entryKind: html-entry
                        - runtime.entryPackagingMode: entry-with-local-dependencies
                        - runtime.runtimeOwnershipMode: companion-owned
                        - runtime.launchRequired: true
                        - runtime.surfaceRequired: true
                        - runtime.acceptanceSignals: page-opens, runtime-surface-renders
                        """
        );

        LlmProvider provider = new NoopReviewProvider();
        StageReviewer reviewer = newStageReviewer(provider);

        ReviewResult result = reviewer.review(
                tempDir,
                dummyRunWithDesign(designPath),
                StageType.IMPLEMENTATION,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(true, true, false, java.util.List.of())
                )
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertEquals(ReviewRevisionRoute.PATCH_CURRENT_STAGE, result.revisionRoute());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, result.implementationPatchTarget());
        assertEquals(ReviewReasonCode.RUNTIME_WIRING_GAP, result.reasonCode());
        assertTrue(result.summary().contains("approved contract"));
    }

    @Test
    void testReviewRejectsWhenRequiredExperienceCoverageIsMissing() {
        LlmProvider provider = new NoopReviewProvider();
        StageReviewer reviewer = newStageReviewer(provider);

        String artifact = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.REVIEW_RESULT,
                new ReviewArtifactPayload(
                        "APPROVED",
                        "NONE",
                        ImplementationPatchTarget.NONE.name(),
                        java.util.List.of(),
                        "PATCH_CURRENT_STAGE",
                        "NONE",
                        "测试通过。",
                        "",
                        "",
                        "",
                        false,
                        0,
                        0
                )
        ) + "\n\n" + StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.QUALITY_LEDGER,
                new QualityLedger(
                        new StructureRiskReport(StructureRiskLevel.HIGH, StructureRiskLevel.HIGH, StructureRiskLevel.HIGH, true, true),
                        new CapabilityMatrix(List.of(
                                new CapabilityMatrixEntry(CapabilitySurface.PAUSE_FREEZE, CapabilityExpectation.REQUIRED, true, "")
                        )),
                        new CoverageLedger(List.of(
                                new CoverageLedgerEntry(CapabilitySurface.PAUSE_FREEZE, true, CoverageLedgerStatus.MISSING, List.of(), "missing")
                        ))
                )
        );

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.TEST, artifact);

        assertEquals(ReviewDecision.REJECTED, result.decision());
        assertTrue(result.changeRequest().contains("体验能力覆盖") || result.evidence().contains("pause-freeze"));
    }

    private StructuredReviewResult structured(ReviewResult result, ReviewSemantics semantics) {
        if (result.fixMode() == FixMode.PATCH && result.implementationPatchTarget() == ImplementationPatchTarget.NONE) {
            result = new ReviewResult(
                    result.decision(),
                    result.fixMode(),
                    result.summary(),
                    result.changeRequest(),
                    result.evidence(),
                    result.actionItems(),
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
            );
        }
        return new StructuredReviewResult(result, semantics);
    }


    private abstract static class StructuredTestLlmProvider implements LlmProvider {
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

    private static final class NoopReviewProvider extends StructuredTestLlmProvider {
        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
            return "";
        }

        @Override
        public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
            return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
        }

        @Override
        public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
            return review(systemPrompt, candidateContent, options);
        }
    }

    private ReviewSemantics openQuestionSemantics() {
        return new ReviewSemantics(true, false, true, true, false, false, false, false, false, false, false, false, false);
    }

    private ReviewSemantics lowAuthoritySemantics() {
        return new ReviewSemantics(true, true, false, false, false, false, false, false, false, false, false, false, false);
    }

    private ReviewSemantics unsupportedHardeningSemantics() {
        return new ReviewSemantics(true, false, false, true, false, true, true, false, false, false, false, false, false);
    }

    private ReviewSemantics downstreamOnlySemantics() {
        return new ReviewSemantics(true, false, false, false, false, true, true, true, false, false, false, false, false);
    }

    private ReviewSemantics performanceClaimWithoutMeasurementSemantics() {
        return new ReviewSemantics(true, false, false, false, false, false, false, false, false, true, false, false, false);
    }

    private RunRecord dummyRun() {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "constraints",
                new RunConfig(Map.of(), 5),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }

    private RunRecord dummyRunWithDesign(Path designPath) {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        states.put(StageType.DESIGN, new StageExecution(StageType.DESIGN, StageStatus.APPROVED, 1, designPath.toString(), ReviewDecision.APPROVED, "ok", ""));
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
