package devflow.agent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.executor.TestExecutor;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageReviewerTests {

    @TempDir
    Path tempDir;

    @Test
    void documentReviewFailsWhenOllamaReturnsEmptyContentAfterRetries() {
        LlmProvider provider = new LlmProvider() {
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

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())
        );

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
        LlmProvider provider = new LlmProvider() {
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

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())
        );

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
        LlmProvider provider = new LlmProvider() {
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

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())
        );

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
    void analysisReviewIgnoresDownstreamDesignDetailsAsBlockingIssues() {
        LlmProvider provider = new LlmProvider() {
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
        };

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())
        );

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
    void prdReviewIgnoresDownstreamTechnicalDetailsAsBlockingIssues() {
        LlmProvider provider = new LlmProvider() {
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
        };

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())
        );

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
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertTrue(result.summary().contains("DESIGN/TEST_CASE"));
    }

    @Test
    void documentReviewRejectsTruncatedPrdContent() {
        LlmProvider provider = new LlmProvider() {
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

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())
        );

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.PRD, """
                # 产品需求文档

                ## 1. 产品目标
                提供一个纯网页版数独体验。

                ## 2. 目标用户与使用场景
                面向休闲解谜用户。

                ## 3. 功能范围
                支持 6x6 与 9x9，支持填数、标记、撤销。

                ## 4. 非功能要求
                页面需要精致，提供清晰反馈与

                ## 5. 验收标准
                用户完成当前题目后自动进入下一题。

                ## 6. 不做什么
                本轮不做排行榜。
                """);

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.summary().contains("结构完整性问题"));
    }

    @Test
    void documentReviewAllowsExtraWellFormedTopLevelSection() {
        LlmProvider provider = new LlmProvider() {
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

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                new TestExecutor(new FileProjectWorkspace(), provider, new ObjectMapper())
        );

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

                ## 7. 当前备注
                这是补充备注。
                """);

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void implementationReviewDefersUnsupportedPerformanceClaimToTestStage() {
        LlmProvider provider = new LlmProvider() {
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
                        ""
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
                return new devflow.agent.executor.SelfCheckResult(true, "项目自测通过。", "未包含性能测量数据。");
            }
        };

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                testExecutor
        );

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
        LlmProvider provider = new LlmProvider() {
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
                        ""
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
                return new devflow.agent.executor.SelfCheckResult(true, "项目自测通过。", "未包含性能测量数据。");
            }
        };

        Path designPath = tempDir.resolve("design.md");
        Files.writeString(designPath, """
                # 技术方案设计

                ## 6. 测试与验证策略
                - 记录 6x6 和 9x9 的生成耗时
                - 使用 benchmark 验证生成时间是否满足 500ms
                """);
        RunRecord runRecord = dummyRunWithDesign(designPath);

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                testExecutor
        );

        ReviewResult result = reviewer.review(tempDir, runRecord, StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.summary().contains("技术方案已要求性能测量"));
        assertTrue(result.changeRequest().contains("DESIGN"));
    }

    @Test
    void implementationReviewDoesNotBlockOnGenericDesignValidationLanguage() throws Exception {
        LlmProvider provider = new LlmProvider() {
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
                        ""
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

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                testExecutor
        );

        ReviewResult result = reviewer.review(tempDir, runRecord, StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertTrue(result.summary().contains("TEST 阶段"));
    }

    @Test
    void implementationReviewBackfillsEvidenceAndActionItemsWhenMissing() {
        LlmProvider provider = new LlmProvider() {
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
                        ""
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

        StageReviewer reviewer = new StageReviewer(
                provider,
                new WorkspaceSnapshotStore(new devflow.agent.orchestrator.FileRunRepository(), new FileProjectWorkspace()),
                testExecutor
        );

        ReviewResult result = reviewer.review(tempDir, dummyRun(), StageType.IMPLEMENTATION, "# 代码实现");

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.evidence().contains("补充支持该结论的直接证据"));
        assertTrue(result.actionItems().contains("定位受影响文件和函数"));
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
