package devflow.agent.executor;

import devflow.agent.quality.CapabilitySurface;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestCasePlannerTests {

    @TempDir
    Path tempDir;

    @Test
    void fallbackIncludesPerformanceCaseWhenDesignDefinesLoadThreshold() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <h1>Sudoku</h1>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        TestCasePlanner planner = new TestCasePlanner(workspace, null, new com.fasterxml.jackson.databind.ObjectMapper());

        TestCasePlan plan = planner.plan(
                tempDir,
                fingerprint,
                "实现一个数独填空游戏",
                "需要纯网页版",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - validation.performanceMeasurementRequired: true
                - validation.pageLoadMaxMs: 2000
                """,
                """
                # 技术方案

                ## 8. Contract Metadata
                - validation.performanceMeasurementRequired: true
                - validation.pageLoadMaxMs: 2000
                """,
                "",
                null
        );

        TestCaseSpec performanceCase = plan.cases().stream()
                .filter(testCase -> "TC-PERF-LOAD".equals(testCase.id()))
                .findFirst()
                .orElseThrow();

        assertEquals("performance", performanceCase.type());
        assertTrue(performanceCase.required());
        assertEquals(TestStepAction.MEASURE_PAGE_LOAD_MAX_MS, performanceCase.steps().getFirst().action());
        assertEquals(2000, performanceCase.steps().getFirst().ms());
    }

    @Test
    void fallbackDerivesButtonSelectorsFromStaticHtmlUsingTreeSitter() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="startBtn">开始</button>
                          <button class="pause-btn">暂停</button>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        TestCasePlanner planner = new TestCasePlanner(workspace, null, new com.fasterxml.jackson.databind.ObjectMapper());

        TestCasePlan plan = planner.plan(
                tempDir,
                fingerprint,
                "实现一个俄罗斯方块",
                "需要纯网页版",
                "# 产品需求文档\n",
                "# 技术方案\n",
                "",
                null
        );

        assertTrue(plan.cases().stream().anyMatch(testCase ->
                testCase.steps().stream().anyMatch(step -> "#startBtn".equals(step.selector()))
        ));
        assertTrue(plan.cases().stream().anyMatch(testCase ->
                testCase.steps().stream().anyMatch(step -> ".pause-btn".equals(step.selector()))
        ));
    }

    @Test
    void fallbackUsesDetectedHtmlEntryWhenIndexIsMissing() throws Exception {
        Files.createDirectories(tempDir.resolve("pages"));
        Files.writeString(
                tempDir.resolve("pages").resolve("play.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="startBtn">开始</button>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        TestCasePlanner planner = new TestCasePlanner(workspace, null, new com.fasterxml.jackson.databind.ObjectMapper());

        TestCasePlan plan = planner.plan(
                tempDir,
                fingerprint,
                "实现一个俄罗斯方块",
                "需要纯网页版",
                "# 产品需求文档\n",
                "# 技术方案\n",
                "",
                null
        );

        assertTrue(plan.cases().stream().allMatch(testCase -> "pages/play.html".equals(testCase.entry())));
    }

    @Test
    void plannerStrengthensRequiredInteractiveCasesWithObservableStateChecks() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <canvas id="board"></canvas>
                          <button id="startBtn">开始</button>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options) {
                return """
                        {
                          "summary": "生成一条很弱的交互用例",
                          "cases": [
                            {
                              "id": "TC-001",
                              "title": "点击开始按钮",
                              "type": "functional",
                              "required": true,
                              "entry": "index.html",
                              "preconditions": "",
                              "expected": "页面继续运行",
                              "steps": [
                                {
                                  "action": "ASSERT_SELECTOR",
                                  "selector": "#startBtn",
                                  "optional": false
                                },
                                {
                                  "action": "CLICK",
                                  "selector": "#startBtn",
                                  "optional": false
                                },
                                {
                                  "action": "ASSERT_NO_ERRORS",
                                  "optional": false
                                }
                              ]
                            }
                          ]
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, java.util.Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestCasePlanner planner = new TestCasePlanner(workspace, provider, new com.fasterxml.jackson.databind.ObjectMapper());
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                1,
                java.util.List.of("body", "canvas", "#startBtn", "button"),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );

        TestCasePlan plan = planner.plan(
                tempDir,
                fingerprint,
                "实现一个俄罗斯方块",
                "需要纯网页版",
                "# 产品需求文档\n",
                "# 技术方案\n",
                "",
                snapshot
        );

        TestCaseSpec testCase = plan.cases().getFirst();
        assertTrue(testCase.steps().stream().anyMatch(step -> step.action() == TestStepAction.SNAPSHOT_CANVAS_HASH));
        assertTrue(testCase.steps().stream().anyMatch(step -> step.action() == TestStepAction.ASSERT_CANVAS_HASH_CHANGED));
    }

    @Test
    void plannerStillAddsObservableStateChecksWhenInteractiveCaseOnlyAssertsStaticText() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="startBtn">开始</button>
                          <p id="status">ready</p>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options) {
                return """
                        {
                          "summary": "生成一条只检查静态文本的交互用例",
                          "cases": [
                            {
                              "id": "TC-001",
                              "title": "点击开始按钮后状态可见",
                              "type": "functional",
                              "required": true,
                              "entry": "index.html",
                              "preconditions": "",
                              "expected": "点击后状态仍显示 ready 文案",
                              "steps": [
                                {
                                  "action": "ASSERT_SELECTOR",
                                  "selector": "#startBtn",
                                  "optional": false
                                },
                                {
                                  "action": "CLICK",
                                  "selector": "#startBtn",
                                  "optional": false
                                },
                                {
                                  "action": "ASSERT_TEXT_CONTAINS",
                                  "selector": "#status",
                                  "text": "ready",
                                  "optional": false
                                }
                              ]
                            }
                          ]
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, java.util.Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestCasePlanner planner = new TestCasePlanner(workspace, provider, new com.fasterxml.jackson.databind.ObjectMapper());
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                0,
                java.util.List.of("body", "#startBtn", "#status", "button"),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );

        TestCasePlan plan = planner.plan(
                tempDir,
                fingerprint,
                "实现一个俄罗斯方块",
                "需要纯网页版",
                "# 产品需求文档\n",
                "# 技术方案\n",
                "",
                snapshot
        );

        TestCaseSpec testCase = plan.cases().getFirst();
        assertTrue(testCase.steps().stream().anyMatch(step -> step.action() == TestStepAction.SNAPSHOT_DOM_SIGNATURE));
        assertTrue(testCase.steps().stream().anyMatch(step -> step.action() == TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED));
    }

    @Test
    void fallbackAddsRuntimeMetricCaseOnlyWhenSnapshotDeclaresMetricExposure() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="startBtn">开始</button>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        TestCasePlanner planner = new TestCasePlanner(workspace, null, new com.fasterxml.jackson.databind.ObjectMapper());

        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                0,
                java.util.List.of("body", "#startBtn", "button"),
                java.util.List.of(WebRuntimeMetricKeys.LAST_ACTION_MS),
                java.util.List.of(),
                java.util.List.of()
        );

        TestCasePlan plan = planner.plan(
                tempDir,
                fingerprint,
                "实现一个俄罗斯方块",
                "需要纯网页版",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - validation.performanceMeasurementRequired: true
                - validation.interactionMaxMs: 120
                """,
                """
                # 技术方案

                ## 8. Contract Metadata
                - validation.performanceMeasurementRequired: true
                - validation.interactionMaxMs: 120
                """,
                "no inline metric hint here",
                snapshot
        );

        TestCaseSpec performanceCase = plan.cases().stream()
                .filter(testCase -> "TC-PERF-RUNTIME".equals(testCase.id()))
                .findFirst()
                .orElseThrow();

        assertEquals(TestStepAction.ASSERT_WINDOW_METRIC_MAX_MS, performanceCase.steps().getFirst().action());
        assertEquals(120, performanceCase.steps().getFirst().ms());
        assertEquals(WebRuntimeMetricKeys.LAST_ACTION_MS, performanceCase.steps().getFirst().text());
    }

    @Test
    void plannerPromptIncludesStructuredContractContext() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="startBtn">开始</button>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        AtomicReference<String> capturedPrompt = new AtomicReference<>("");
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options) {
                capturedPrompt.set(userPrompt);
                return """
                        {
                          "summary": "ok",
                          "cases": []
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, java.util.Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestCasePlanner planner = new TestCasePlanner(workspace, provider, new com.fasterxml.jackson.databind.ObjectMapper());
        planner.plan(
                tempDir,
                fingerprint,
                "实现一个可玩的网页版俄罗斯方块",
                "需要纯网页版、可直接打开运行",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders
                """,
                "",
                null
        );

        assertTrue(capturedPrompt.get().contains("结构化契约"));
        assertTrue(capturedPrompt.get().contains("runtime.entryKind"));
        assertTrue(capturedPrompt.get().contains("能力矩阵"));
        assertTrue(capturedPrompt.get().contains(CapabilitySurface.PAGE_LOAD.wireValue()));
    }

    @Test
    void plannerBackfillsMissingRequiredCapabilityCoverageFromFallback() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="startBtn">开始</button>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options) {
                return """
                        {
                          "summary": "只返回一条交互用例",
                          "cases": [
                            {
                              "id": "TC-ONLY-INTERACTIVE",
                              "title": "开始按钮可点击",
                              "type": "functional",
                              "required": true,
                              "entry": "index.html",
                              "preconditions": "",
                              "expected": "点击后页面不报错",
                              "capabilities": ["primary-interaction"],
                              "steps": [
                                {"action": "ASSERT_SELECTOR", "selector": "#startBtn", "optional": false},
                                {"action": "CLICK", "selector": "#startBtn", "optional": false},
                                {"action": "ASSERT_NO_ERRORS", "optional": false}
                              ]
                            }
                          ]
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, java.util.Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestCasePlanner planner = new TestCasePlanner(workspace, provider, new com.fasterxml.jackson.databind.ObjectMapper());
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Demo",
                12,
                0,
                java.util.List.of("body", "#startBtn", "button"),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );

        TestCasePlan plan = planner.plan(
                tempDir,
                fingerprint,
                "实现一个可交互网页",
                "需要纯网页版、可直接打开运行",
                "# 产品需求文档\n",
                "# 技术方案\n",
                "",
                snapshot
        );

        assertTrue(plan.cases().stream().anyMatch(testCase -> "TC-SMOKE-LOAD".equals(testCase.id())));
        assertTrue(plan.qualityPlan().capabilityMatrix().requires(CapabilitySurface.PAGE_LOAD));
    }

    @Test
    void plannerUsesDynamicOutputBudgetRatioInsteadOfFixedNumPredict() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body><button id="startBtn">开始</button></body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        AtomicReference<java.util.Map<String, Object>> capturedOptions = new AtomicReference<>(java.util.Map.of());
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options) {
                capturedOptions.set(options == null ? java.util.Map.of() : java.util.Map.copyOf(options));
                return """
                        {
                          "summary": "ok",
                          "cases": []
                        }
                        """;
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options, ModelRole role) {
                return generate(systemPrompt, userPrompt, options);
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, java.util.Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestCasePlanner planner = new TestCasePlanner(workspace, provider, new com.fasterxml.jackson.databind.ObjectMapper());
        planner.plan(
                tempDir,
                fingerprint,
                "实现一个可交互网页",
                "需要纯网页版、可直接打开运行",
                "# 产品需求文档\n",
                "# 技术方案\n",
                "",
                null
        );

        assertTrue(capturedOptions.get().containsKey(LlmOptionKeys.OUTPUT_BUDGET_RATIO));
        assertFalse(capturedOptions.get().containsKey("num_predict"));
    }

    @Test
    void plannerPromptUsesCapabilityCoverageLanguageInsteadOfProductSpecificHint() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body><button id="startBtn">开始</button></body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ProjectFingerprint fingerprint = new ProjectInspector(workspace).inspect(tempDir);
        AtomicReference<String> capturedSystemPrompt = new AtomicReference<>("");
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options) {
                capturedSystemPrompt.set(systemPrompt);
                return """
                        {
                          "summary": "ok",
                          "cases": []
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, java.util.Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestCasePlanner planner = new TestCasePlanner(workspace, provider, new com.fasterxml.jackson.databind.ObjectMapper());
        planner.plan(
                tempDir,
                fingerprint,
                "实现一个俄罗斯方块",
                "需要纯网页版、可直接打开运行",
                "# 产品需求文档\n",
                "# 技术方案\n",
                "",
                null
        );

        assertFalse(capturedSystemPrompt.get().contains("如果是网页/小游戏"));
        assertTrue(capturedSystemPrompt.get().contains("required capability surface"));
        assertFalse(capturedSystemPrompt.get().contains("2 到 5"));
    }
}
