package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.util.ProjectPathSupport;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class FileEditCoordinatorTests {

    @TempDir
    Path tempDir;

    @Test
    void existingCodeFileUsesLocalEditInsteadOfWholeFileRewrite() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean wholeFileCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("请只输出目标文件的完整最终内容")) {
                    wholeFileCalled.set(true);
                    return "function tick() { return 999; }";
                }
                if (systemPrompt.contains("符号级精确改写")) {
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "function tick() {",
                            3,
                            "function tick() {",
                            "  return 1;",
                            "}"
                    );
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), """
                function tick() {
                  return 0;
                }
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game.js"),
                """
                计划：补齐游戏逻辑
                """,
                new Subtask(
                        "补齐逻辑",
                        "实现 tick 的真实逻辑",
                        List.of(),
                        List.of("tick 返回有效状态"),
                        List.of(),
                        List.of("tick 返回 1"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐 tick"))
                ),
                null,
                "",
                "补齐逻辑",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertTrue(generated.contains("return 1;"));
        assertFalse(wholeFileCalled.get(), "已有代码文件不应再静默走 whole-file 重写主路径");
    }

    @Test
    void existingCodeFileWithoutStableLocalBoundaryFailsInsteadOfFallingBackToWholeFile() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean wholeFileCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("请只输出目标文件的完整最终内容")) {
                    wholeFileCalled.set(true);
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), "const broken =");

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeGenerateFileContent(
                        coordinator,
                        Path.of("game.js"),
                        "计划：修复损坏的文件",
                        new Subtask(
                                "修复 game.js",
                                "修复现有代码文件",
                                List.of(),
                                List.of("修复 game.js"),
                                List.of(),
                                List.of("文件恢复可解析"),
                                false,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("game.js", ChangeAction.WRITE, "修复现有文件"))
                        ),
                        null,
                        "",
                        "修复现有文件",
                        new SubtaskExecutionState(DeliveryMode.PATCH, false)
                )
        );

        assertTrue(exception.getCause() instanceof GenerationFailureException);
        assertFalse(wholeFileCalled.get(), "局部边界缺失时应显式失败，而不是回退成 whole-file");
    }

    @Test
    void newCodeFileUsesAppendOnlyLocalEditInsteadOfWholeFileRewrite() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean wholeFileCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("请只输出目标文件的完整最终内容")) {
                    wholeFileCalled.set(true);
                    return "export function tick() { return 1; }";
                }
                if (systemPrompt.contains("符号级精确改写")) {
                    if (systemPrompt.contains("当前文件为空或新建文件")) {
                        return StructuredDiffTestSupport.appendAtEnd(
                                userPrompt,
                                "export function tick() {",
                                "}"
                        );
                    }
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "export function tick() {",
                            2,
                            "export function tick() {",
                            "  return 1;",
                            "}"
                    );
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game.js"),
                "计划：建立游戏核心模块",
                new Subtask(
                        "建立核心模块",
                        "创建 game.js 的最小模块骨架",
                        List.of(),
                        List.of("存在可调用的 tick"),
                        List.of(),
                        List.of("文件可解析"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "建立核心模块"))
                ),
                null,
                "",
                "建立核心模块",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertTrue(generated.contains("export function tick()"));
        assertFalse(wholeFileCalled.get(), "新建代码文件也不应默认退回 whole-file");
    }

    @Test
    void scaffoldFollowUpCodeUnitsUseStrictSingleSymbolLeafUnits() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger codeUnitAllCalls = new AtomicInteger();
        AtomicInteger leafUnitCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (!systemPrompt.contains("符号级精确改写")) {
                    return "";
                }
                if (userPrompt.contains("label: main.js#code-unit-all")) {
                    codeUnitAllCalls.incrementAndGet();
                    return StructuredDiffTestSupport.appendAtEnd(
                            userPrompt,
                            "function alpha() {",
                            "}",
                            "",
                            "function beta() {",
                            "}",
                            "",
                            "function gamma() {",
                            "}",
                            "",
                            "function delta() {",
                            "}",
                            "",
                            "function epsilon() {",
                            "}"
                    );
                }
                if (userPrompt.contains("label: main.js#code-unit-")) {
                    leafUnitCalls.incrementAndGet();
                    String symbol = extractFirstAllowedSymbol(userPrompt);
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "function %s() {".formatted(symbol),
                            2,
                            "function %s() {".formatted(symbol),
                            "  return 33;",
                            "}"
                    );
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("main.js"),
                "计划：先建立主模块骨架，再逐步补齐移动逻辑",
                new Subtask(
                        "补齐 main.js 逻辑",
                        "建立主模块骨架并补齐核心逻辑",
                        List.of(),
                        List.of("main.js 可解析", "核心函数存在"),
                        List.of(),
                        List.of("主模块包含多个顶层函数"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("main.js", ChangeAction.WRITE, "补齐主模块骨架与核心逻辑"))
                ),
                null,
                "",
                "补齐 main.js 逻辑",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertEquals(1, codeUnitAllCalls.get());
        assertEquals(5, leafUnitCalls.get(), "scaffold 之后应直接进入五个单 symbol leaf unit");
        assertTrue(generated.contains("function alpha()"));
        assertTrue(generated.contains("return 33;"));
        assertTrue(generated.contains("function epsilon()"));
    }

    @Test
    void newCodeFileScaffoldIsExpandedIntoFollowUpSymbolUnits() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger scaffoldCalls = new AtomicInteger();
        AtomicInteger followUpCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (!systemPrompt.contains("符号级精确改写")) {
                    return "";
                }
                if (systemPrompt.contains("当前文件为空或新建文件")) {
                    scaffoldCalls.incrementAndGet();
                    return StructuredDiffTestSupport.appendAtEnd(
                            userPrompt,
                            "export function alpha() {",
                            "}",
                            "",
                            "export function beta() {",
                            "}"
                    );
                }
                if (userPrompt.contains("allowedSymbols: alpha")) {
                    followUpCalls.incrementAndGet();
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "export function alpha() {",
                            2,
                            "export function alpha() {",
                            "  return 1;",
                            "}"
                    );
                }
                if (userPrompt.contains("allowedSymbols: beta")) {
                    followUpCalls.incrementAndGet();
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "export function beta() {",
                            2,
                            "export function beta() {",
                            "  return 2;",
                            "}"
                    );
                }
                fail("空文件骨架成功后应继续进入按符号补实现路径");
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game-engine.js"),
                "计划：建立游戏核心模块",
                new Subtask(
                        "建立核心模块",
                        "创建 game-engine.js 的最小模块骨架，并逐步补实现",
                        List.of(),
                        List.of("核心函数存在且可继续增量编辑"),
                        List.of(),
                        List.of("文件可解析"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game-engine.js", ChangeAction.WRITE, "建立核心模块"))
                ),
                null,
                "",
                "建立核心模块",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertEquals(1, scaffoldCalls.get(), "空文件第一轮只应建立一次最小骨架");
        assertEquals(2, followUpCalls.get(), "骨架建立后应立即按单 symbol leaf unit 继续补实现");
        assertTrue(generated.contains("return 1;"));
        assertTrue(generated.contains("return 2;"));
    }

    @Test
    void preciseCodeInvalidJsonIsDeterministicallyRepaired() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("符号级精确改写")) {
                    return StructuredDiffTestSupport.malformedJson(
                            StructuredDiffTestSupport.replaceRangeStartingAt(
                                    userPrompt,
                                    "function tick() {",
                                    3,
                                    "function tick() {",
                                    "  const next = 1;",
                                    "  return next;",
                                    "}"
                            )
                    );
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), """
                function tick() {
                  return 0;
                }
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                1
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game.js"),
                "计划：补齐游戏逻辑",
                new Subtask(
                        "补齐逻辑",
                        "实现 tick 的真实逻辑",
                        List.of(),
                        List.of("tick 返回有效状态"),
                        List.of(),
                        List.of("tick 返回 1"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐 tick"))
                ),
                null,
                "",
                "补齐逻辑",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertTrue(generated.contains("return next;"));
    }

    @Test
    void truncatedPreciseCodeUnitIsSplitInsteadOfRetryingSameOversizedUnit() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger oversizedUnitCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (!systemPrompt.contains("符号级精确改写")) {
                    return "";
                }
                if (userPrompt.contains("allowedSymbols: alpha, beta, gamma, delta")) {
                    if (oversizedUnitCalls.incrementAndGet() > 1) {
                        fail("截断后不应继续重试同一个过大的 edit unit");
                    }
                    throw new LlmInvocationException(LlmFailureReason.OUTPUT_TRUNCATED, "unit truncated");
                }
                String targetSymbol = extractFirstAllowedSymbol(userPrompt);
                return StructuredDiffTestSupport.replaceRangeStartingAt(
                        userPrompt,
                        "function %s() {".formatted(targetSymbol),
                        3,
                        "function %s() {".formatted(targetSymbol),
                        "  return 1;",
                        "}"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), """
                function alpha() {
                  return 0;
                }

                function beta() {
                  return 0;
                }

                function gamma() {
                  return 0;
                }

                function delta() {
                  return 0;
                }
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                3
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game.js"),
                "计划：按单元增量补齐代码逻辑",
                new Subtask(
                        "补齐多个函数",
                        "逐步完善 game.js 中的函数实现",
                        List.of(),
                        List.of("核心函数逻辑已补齐"),
                        List.of(),
                        List.of("文件可解析"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐多个函数"))
                ),
                null,
                "",
                "补齐多个函数",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertEquals(0, oversizedUnitCalls.get(), "过大的 edit unit 应在发送前先拆小，不再先执行一次再截断");
        assertTrue(generated.contains("function alpha()"));
    }

    @Test
    void singleSymbolCodeUnitUsesBodyOnlyPromptAndReducedBudget() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> capturedSystemPrompt = new AtomicReference<>("");
        AtomicReference<Double> capturedOutputBudgetRatio = new AtomicReference<>(0.0d);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (!systemPrompt.contains("符号级精确改写")) {
                    return "";
                }
                capturedSystemPrompt.set(systemPrompt);
                capturedOutputBudgetRatio.set(((Number) options.get(LlmOptionKeys.OUTPUT_BUDGET_RATIO)).doubleValue());
                return StructuredDiffTestSupport.replaceRangeStartingAt(
                        userPrompt,
                        "function tick() {",
                        3,
                        "function tick() {",
                        "  return 1;",
                        "}"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), """
                function tick() {
                  return 0;
                }
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game.js"),
                "计划：补齐单个符号逻辑",
                new Subtask(
                        "补齐 tick",
                        "实现 tick 的真实逻辑",
                        List.of(),
                        List.of("tick 返回有效状态"),
                        List.of(),
                        List.of("tick 返回 1"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐 tick"))
                ),
                null,
                "",
                "补齐 tick",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertTrue(capturedSystemPrompt.get().contains("当前编辑单元已缩到单个受限符号"));
        assertTrue(capturedSystemPrompt.get().contains("hunk 只能覆盖 \"tick\" 对应的现有实现区域"));
        assertTrue(capturedSystemPrompt.get().contains("不要额外追加 helper"));
        assertEquals(GenerationBudgetProfile.preciseCodeUnitOutputRatio(), capturedOutputBudgetRatio.get());
        assertTrue(generated.contains("return 1;"));
    }

    @Test
    void restrictedCodeUnitRejectsAppendFilePatch() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (!systemPrompt.contains("符号级精确改写")) {
                    return "";
                }
                return StructuredDiffTestSupport.appendAtEnd(
                        userPrompt,
                        "function helper() {",
                        "  return 1;",
                        "}"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), """
                function tick() {
                  return 0;
                }
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                1
        );

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeGenerateFileContent(
                        coordinator,
                        Path.of("game.js"),
                        "计划：补齐单个符号逻辑",
                        new Subtask(
                                "补齐 tick",
                                "实现 tick 的真实逻辑",
                                List.of(),
                                List.of("tick 返回有效状态"),
                                List.of(),
                                List.of("tick 返回 1"),
                                false,
                                DeliveryMode.INCREMENTAL,
                                List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐 tick"))
                        ),
                        null,
                        "",
                        "补齐 tick",
                        new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
                )
        );

        assertTrue(exception.getCause() instanceof GenerationFailureException);
        GenerationFailureException failure = (GenerationFailureException) exception.getCause();
        assertEquals(GenerationFailureType.RESULT_FILE_INVALID, failure.report().failureType());
    }

    @Test
    void outOfScopePreciseCodeUnitIsSplitInsteadOfRetryingSameOversizedUnit() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger oversizedUnitCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (!systemPrompt.contains("符号级精确改写")) {
                    return "";
                }
                if (userPrompt.contains("allowedSymbols: alpha, beta, gamma, delta")) {
                    if (oversizedUnitCalls.incrementAndGet() > 1) {
                        fail("edit unit 越界后不应继续重试同一个过大的单元");
                    }
                    return StructuredDiffTestSupport.appendAtEnd(
                            userPrompt,
                            "function omega() {",
                            "  return 9;",
                            "}"
                    );
                }
                String targetSymbol = extractFirstAllowedSymbol(userPrompt);
                return StructuredDiffTestSupport.replaceRangeStartingAt(
                        userPrompt,
                        "function %s() {".formatted(targetSymbol),
                        3,
                        "function %s() {".formatted(targetSymbol),
                        "  return 2;",
                        "}"
                );
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), """
                function alpha() {
                  return 0;
                }

                function beta() {
                  return 0;
                }

                function gamma() {
                  return 0;
                }

                function delta() {
                  return 0;
                }
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                3
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game.js"),
                "计划：按单元增量补齐代码逻辑",
                new Subtask(
                        "补齐多个函数",
                        "逐步完善 game.js 中的函数实现",
                        List.of(),
                        List.of("核心函数逻辑已补齐"),
                        List.of(),
                        List.of("文件可解析"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐多个函数"))
                ),
                null,
                "",
                "补齐多个函数",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertEquals(0, oversizedUnitCalls.get(), "越界的大单元应在发送前先拆小，不再先执行一次再截断");
        assertTrue(generated.contains("function alpha()"));
        assertTrue(generated.contains("return 2;"));
    }

    @Test
    void inlineStyleWorksetIsPreferredBeforeFocusedStyleRegion() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean inlineStyleWorksetCalled = new AtomicBoolean(false);
        AtomicBoolean focusedStyleCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("独立样式工作集")) {
                    inlineStyleWorksetCalled.set(true);
                    if (userPrompt.contains("allowedSymbols: #panel")) {
                        return StructuredDiffTestSupport.appendAtEnd(
                                userPrompt,
                                "#panel {",
                                "  display: grid;",
                                "  gap: 12px;",
                                "}"
                        );
                    }
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "#app {",
                            3,
                            "#app {",
                            "  color: blue;",
                            "}"
                    );
                }
                if (systemPrompt.contains("请只改写 HTML 中 <style id=\"app-style\">")) {
                    focusedStyleCalled.set(true);
                    return "#app { color: green; }";
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    #app {
                      color: red;
                    }
                  </style>
                </head>
                <body>
                  <main id="app-root"></main>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：补齐页面样式骨架",
                new Subtask(
                        "补齐样式",
                        "通过主样式锚点补齐最小可运行样式",
                        List.of(),
                        List.of("页面样式可继续增量补齐"),
                        List.of(),
                        List.of("HTML 结构保持可解析"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐页面样式"))
                ),
                null,
                "",
                "补齐页面样式",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertTrue(inlineStyleWorksetCalled.get(), "内联样式应优先进入样式工作集 patch 主链");
        assertFalse(focusedStyleCalled.get(), "样式工作集可用时不应先退回 focused-style 路径");
        assertTrue(generated.contains("color: blue;"));
    }

    @Test
    void multiRuleInlineStylePrefersInlineStyleWorksetBeforeFocusedFallback() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean inlineStyleWorksetCalled = new AtomicBoolean(false);
        AtomicBoolean focusedStyleCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("独立样式工作集")) {
                    inlineStyleWorksetCalled.set(true);
                    if (userPrompt.contains("#panel")) {
                        return StructuredDiffTestSupport.replaceRangeStartingAt(
                                userPrompt,
                                "#panel {",
                                3,
                                "#panel {",
                                "  display: grid;",
                                "  gap: 12px;",
                                "}"
                        );
                    }
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "#app {",
                            3,
                            "#app {",
                            "  color: blue;",
                            "}"
                    );
                }
                if (systemPrompt.contains("请只改写 HTML 中 <style id=\"app-style\">")) {
                    focusedStyleCalled.set(true);
                    return """
                            #app {
                              color: blue;
                            }

                            #panel {
                              display: grid;
                              gap: 12px;
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    #app {
                      color: red;
                    }
                    #panel {
                      display: block;
                    }
                  </style>
                </head>
                <body>
                  <main id="app-root"></main>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：对多规则主样式做整体补齐",
                new Subtask(
                        "补齐多规则样式",
                        "补齐多个样式规则并保持宿主 HTML 可解析",
                        List.of(),
                        List.of("样式规则完整"),
                        List.of(),
                        List.of("HTML 结构保持可解析"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐多规则样式", FileEditScope.INLINE_STYLE_PATCH))
                ),
                null,
                "",
                "补齐多规则样式",
                new SubtaskExecutionState(DeliveryMode.PATCH, true)
        );

        assertTrue(inlineStyleWorksetCalled.get(), "多规则内联样式应继续优先进入样式工作集 patch 主链");
        assertTrue(
                generated.contains("gap: 12px;"),
                "即使多规则样式最终需要退回 focused-style，也应先尝试样式工作集 patch 主链"
        );
    }

    @Test
    void inlineScriptSymbolFailureFallsBackToFocusedScriptRegionBeforePreciseHtml() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean preciseHtmlCalled = new AtomicBoolean(false);
        AtomicBoolean focusedScriptCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("符号级精确改写")) {
                    return StructuredDiffTestSupport.staleHash(
                            StructuredDiffTestSupport.replaceRangeStartingAt(
                                    userPrompt,
                                    "function bootstrap() {",
                                    5,
                                    "function bootstrap() {",
                                    "  return;",
                                    "}"
                            )
                    );
                }
                if (systemPrompt.contains("script id=\"app-script\"")) {
                    focusedScriptCalled.set(true);
                    return """
                            function bootstrap() {
                              const canvas = document.getElementById('game-canvas');
                              const ctx = canvas.getContext('2d');
                              ctx.clearRect(0, 0, canvas.width, canvas.height);
                            }

                            document.addEventListener('DOMContentLoaded', () => {
                              bootstrap();
                            });
                            """;
                }
                if (systemPrompt.contains("精确改写")) {
                    preciseHtmlCalled.set(true);
                    return """
                            {
                              "markupHtml": null,
                              "styleCss": null,
                              "scriptJs": "console.log('unexpected precise html')",
                              "headAppendHtml": null,
                              "bodyAppendHtml": null
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!DOCTYPE html>
                <html>
                <head>
                  <style id="app-style">
                    #game-canvas { width: 200px; height: 400px; }
                  </style>
                </head>
                <body>
                  <main id="app-root">
                    <canvas id="game-canvas" width="200" height="400"></canvas>
                    <canvas id="next-piece-canvas" width="100" height="100"></canvas>
                    <div id="score">0</div>
                    <button id="start-btn">开始</button>
                    <button id="pause-btn">暂停</button>
                    <button id="reset-btn">重开</button>
                  </main>
                  <script id="app-script">
                    function bootstrap() {
                      const canvas = document.getElementById('game-canvas');
                      const ctx = canvas.getContext('2d');
                      ctx.fillRect(0, 0, 10, 10);
                    }

                    document.addEventListener('DOMContentLoaded', () => {
                      bootstrap();
                    });
                  </script>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        try {
            invokeGenerateFileContent(
                    coordinator,
                    Path.of("index.html"),
                    "计划：在现有入口中补齐核心逻辑",
                    new Subtask(
                            "补齐脚本逻辑",
                            "在现有入口中补齐核心逻辑",
                            List.of(),
                            List.of("页面可交互"),
                            List.of(),
                            List.of("脚本逻辑完整"),
                            false,
                            DeliveryMode.INCREMENTAL,
                            List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐脚本逻辑"))
                    ),
                    null,
                    "",
                    "补齐脚本逻辑",
                    new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
            );
        } catch (InvocationTargetException exception) {
            assertTrue(exception.getCause() instanceof GenerationFailureException);
        }

        assertTrue(focusedScriptCalled.get(), "内联脚本精确编辑命中符号失败后，应优先退到 script 区块级改写");
        assertFalse(preciseHtmlCalled.get(), "这类脚本不应先回到整页 precise-html");
    }

    @Test
    void preciseCodeUsesDeterministicJsonRepairBeforeRegeneratingPatch() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger preciseCodeCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("JSON 载荷修复器")) {
                    fail("这类 JSON 应先被本地 deterministic repair 吸收");
                }
                if (systemPrompt.contains("符号级精确改写")) {
                    preciseCodeCalls.incrementAndGet();
                    return StructuredDiffTestSupport.malformedJson(
                            StructuredDiffTestSupport.replaceRangeStartingAt(
                                    userPrompt,
                                    "function tick() {",
                                    3,
                                    "function tick() {",
                                    "  return 1;",
                                    "}"
                            )
                    );
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), """
                function tick() {
                  return 0;
                }
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                1
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game.js"),
                "计划：补齐游戏逻辑",
                new Subtask(
                        "补齐逻辑",
                        "实现 tick 的真实逻辑",
                        List.of(),
                        List.of("tick 返回有效状态"),
                        List.of(),
                        List.of("tick 返回 1"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐 tick"))
                ),
                null,
                "",
                "补齐逻辑",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertTrue(generated.contains("return 1;"));
        assertEquals(1, preciseCodeCalls.get(), "deterministic JSON repair 不应触发整轮重生成");
    }

    @Test
    void resumedPreciseCodeSplitsRestrictedParentUnitBeforeExecution() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger preciseCodeCalls = new AtomicInteger();
        AtomicBoolean parentUnitPromptSeen = new AtomicBoolean(false);
        String existingContent = """
                function alpha() {
                  return 0;
                }

                function beta() {
                  return 0;
                }
                """;
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("符号级精确改写")) {
                    preciseCodeCalls.incrementAndGet();
                    if (userPrompt.contains("- allowedSymbols: alpha, beta")) {
                        parentUnitPromptSeen.set(true);
                        fail("受限多符号 parent unit 不应直接进入 precise-code 生成");
                    }
                    String symbol = extractFirstAllowedSymbol(userPrompt);
                    String body = "alpha".equals(symbol) ? "return 1;" : "return 2;";
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "function %s() {".formatted(symbol),
                            3,
                            "function %s() {".formatted(symbol),
                            "  %s".formatted(body),
                            "}"
                    );
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("game.js"), existingContent);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        executionState.recordPatchProgress(new FilePatchProgressState(
                Path.of("game.js"),
                FileEditStrategyNames.PRECISE_CODE,
                existingContent,
                List.of(new EditUnit(
                        EditUnitKind.CODE_SYMBOL_BATCH,
                        "game.js#code-unit-1-a",
                        List.of("alpha", "beta"),
                        0,
                        1
                ))
        ));

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("game.js"),
                "计划：继续完成剩余符号实现",
                new Subtask(
                        "继续补齐逻辑",
                        "继续补齐 alpha 与 beta 的剩余实现",
                        List.of(),
                        List.of("alpha 与 beta 均已实现"),
                        List.of(),
                        List.of("文件保持可解析"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("game.js", ChangeAction.WRITE, "继续补齐逻辑"))
                ),
                null,
                "",
                "继续补齐逻辑",
                executionState
        );

        assertFalse(parentUnitPromptSeen.get());
        assertEquals(2, preciseCodeCalls.get(), "parent unit 应先拆成两个 leaf unit，再分别生成");
        assertTrue(generated.contains("return 1;"));
        assertTrue(generated.contains("return 2;"));
    }

        @Test
    void hostHtmlPatchScopePrefersPreciseHtmlOverInlineScriptPath() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean preciseHtmlCalled = new AtomicBoolean(false);
        AtomicBoolean inlineScriptCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("当前 HTML 入口文件里的主脚本已被抽成独立代码工作集")) {
                    inlineScriptCalled.set(true);
                    return StructuredDiffTestSupport.appendAtEnd(
                            userPrompt,
                            "function shouldNotRun() {}"
                    );
                }
                if (systemPrompt.contains("精确改写")) {
                    preciseHtmlCalled.set(true);
                    return """
                            {
                              "markupHtml": "<section class=\\"playfield\\"></section>",
                              "styleCss": "body { background: #111; }",
                              "scriptJs": "window.hostPatchReady = true;",
                              "headAppendHtml": null,
                              "bodyAppendHtml": null
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    body { margin: 0; }
                  </style>
                </head>
                <body>
                  <main id="app-root"><p>loading</p></main>
                  <script id="app-script">
                    function bootstrap() {}
                  </script>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：对现有 HTML 入口做宿主级多区块 patch",
                new Subtask(
                        "补齐宿主入口",
                        "同时补齐入口内容、样式和接线",
                        List.of(),
                        List.of("入口内容完整"),
                        List.of(),
                        List.of("HTML 结构保持可解析"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐宿主入口", FileEditScope.HOST_HTML_PATCH))
                ),
                null,
                "",
                "补齐宿主入口",
                new SubtaskExecutionState(DeliveryMode.PATCH, true)
        );

        assertTrue(preciseHtmlCalled.get(), "HOST_HTML_PATCH 应优先走宿主级 precise-html");
        assertFalse(inlineScriptCalled.get(), "HOST_HTML_PATCH 不应先进入内联脚本 patch 路径");
        assertTrue(generated.contains("window.hostPatchReady = true;"));
        assertTrue(generated.contains("<section class=\"playfield\">"));
    }

    @Test
    void preciseHtmlInvalidJsonNarrowsToFocusedScriptAfterSingleWideAttempt() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger preciseHtmlCalls = new AtomicInteger();
        AtomicInteger focusedScriptCalls = new AtomicInteger();
        AtomicInteger jsonRepairCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("JSON 载荷修复器")) {
                    jsonRepairCalls.incrementAndGet();
                    return "still invalid json";
                }
                if (systemPrompt.contains("请对现有 HTML 页面做“精确改写”")) {
                    preciseHtmlCalls.incrementAndGet();
                    return "not-json";
                }
                if (systemPrompt.contains("请只改写 HTML 中 <script id=\"app-script\"> 的内部 JavaScript")) {
                    focusedScriptCalls.incrementAndGet();
                    return """
                            function bootstrap() {
                              window.focusedReady = true;
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    body { margin: 0; }
                  </style>
                </head>
                <body>
                  <main id="app-root"><p>loading</p></main>
                  <script id="app-script">
                    function bootstrap() {}
                  </script>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：优先补齐宿主脚本接线",
                new Subtask(
                        "补齐宿主入口",
                        "在现有入口中补齐脚本接线",
                        List.of(),
                        List.of("入口脚本可执行"),
                        List.of(),
                        List.of("页面可加载"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐宿主入口", FileEditScope.HOST_HTML_PATCH))
                ),
                null,
                "",
                "补齐宿主入口",
                new SubtaskExecutionState(DeliveryMode.PATCH, true)
        );

        assertEquals(1, preciseHtmlCalls.get(), "宽 precise-html 路径失败后不应继续同构重试");
        assertEquals(1, jsonRepairCalls.get(), "precise-html 结构化 JSON 应只走一次模型修复");
        assertEquals(1, focusedScriptCalls.get(), "宽协议失败后应直接收窄到 focused script region");
        assertTrue(generated.contains("window.focusedReady = true;"));
    }

    @Test
    void inlineScriptPatchScopeUsesFocusedScriptRegionForSingleEntryScriptEvenWhenHostHasStyleAnchor() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean preciseHtmlCalled = new AtomicBoolean(false);
        AtomicBoolean inlineScriptWorksetCalled = new AtomicBoolean(false);
        AtomicBoolean focusedScriptCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("当前 HTML 入口文件里的主脚本已被抽成独立代码工作集")) {
                    inlineScriptWorksetCalled.set(true);
                    fail("单入口脚本不应再进入 inline-script-workset");
                }
                if (systemPrompt.contains("script id=\"app-script\"")) {
                    focusedScriptCalled.set(true);
                    return """
                            function bindButton() {
                              document.getElementById('start-btn')?.addEventListener('click', bootstrap);
                            }

                            function bootstrap() {
                              bindButton();
                            }
                            """;
                }
                if (systemPrompt.contains("精确改写")) {
                    preciseHtmlCalled.set(true);
                    return """
                            {
                              "markupHtml": null,
                              "styleCss": null,
                              "scriptJs": "console.log('should not use precise html')",
                              "headAppendHtml": null,
                              "bodyAppendHtml": null
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <style id="app-style">
                    body { background: #111; }
                  </style>
                </head>
                <body>
                  <main id="app-root"><button id="start-btn">开始</button></main>
                  <script id="app-script">
                    function bootstrap() {}
                  </script>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：只补齐入口脚本逻辑",
                new Subtask(
                        "补齐脚本逻辑",
                        "仅补齐脚本逻辑，不修改宿主样式与结构",
                        List.of(),
                        List.of("按钮绑定完成"),
                        List.of(),
                        List.of("页面可加载"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐脚本逻辑", FileEditScope.INLINE_SCRIPT_PATCH))
                ),
                null,
                "",
                "补齐脚本逻辑",
                new SubtaskExecutionState(DeliveryMode.PATCH, true)
        );

        assertFalse(inlineScriptWorksetCalled.get(), "单入口 INLINE_SCRIPT_PATCH 不应再走 inline-script-workset");
        assertTrue(focusedScriptCalled.get(), "单入口 INLINE_SCRIPT_PATCH 应直接走 focused script region");
        assertFalse(preciseHtmlCalled.get(), "INLINE_SCRIPT_PATCH 不应先回到宿主级 precise-html");
        assertTrue(generated.contains("id=\"app-script\""), "内联脚本 patch 完成后应继续回填宿主脚本锚点");
        assertTrue(generated.contains("function bindButton()"));
    }

    @Test
    void singleEntryInlineScriptUsesFocusedScriptRegionWhenPrecisePreferenceIsDisabled() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean wholeFileCalled = new AtomicBoolean(false);
        AtomicBoolean inlineScriptWorksetCalled = new AtomicBoolean(false);
        AtomicBoolean focusedScriptCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("请只输出目标文件的完整最终内容")) {
                    wholeFileCalled.set(true);
                    return "";
                }
                if (systemPrompt.contains("当前 HTML 入口文件里的主脚本已被抽成独立代码工作集")) {
                    inlineScriptWorksetCalled.set(true);
                    fail("单入口脚本不应再进入 inline-script-workset");
                }
                if (systemPrompt.contains("script id=\"app-script\"")) {
                    focusedScriptCalled.set(true);
                    return """
                            function updateScore(value) {
                              const score = document.getElementById('score');
                              score.textContent = value;
                            }

                            function bootstrap() {
                              updateScore('1');
                            }

                            document.addEventListener('DOMContentLoaded', bootstrap);
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!DOCTYPE html>
                <html>
                <body>
                  <main id="app-root">
                    <div id="score">0</div>
                  </main>
                  <script id="app-script">
                    function bootstrap() {
                    }

                    document.addEventListener('DOMContentLoaded', bootstrap);
                  </script>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：补齐现有入口中的脚本逻辑",
                new Subtask(
                        "补齐脚本逻辑",
                        "补齐现有入口中的脚本逻辑",
                        List.of(),
                        List.of("分数显示可更新"),
                        List.of(),
                        List.of("页面可加载"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐脚本逻辑"))
                ),
                null,
                "",
                "补齐脚本逻辑",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertFalse(wholeFileCalled.get(), "单入口脚本不应退回 whole-file");
        assertFalse(inlineScriptWorksetCalled.get(), "单入口脚本不应再走 inline-script-workset");
        assertTrue(focusedScriptCalled.get(), "单入口脚本应直接走 focused script region");
        assertTrue(generated.contains("function updateScore(value)"));
        assertTrue(generated.contains("updateScore('1');"));
    }

    @Test
    void staleSingleEntryInlineScriptProgressIsDiscardedInsteadOfResumed() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean inlineScriptWorksetCalled = new AtomicBoolean(false);
        AtomicBoolean focusedScriptCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("当前 HTML 入口文件里的主脚本已被抽成独立代码工作集")) {
                    inlineScriptWorksetCalled.set(true);
                    fail("失配的 inline-script-workset progress 不应被继续恢复");
                }
                if (systemPrompt.contains("script id=\"app-script\"")) {
                    focusedScriptCalled.set(true);
                    return """
                            function updateScore(value) {
                              document.getElementById('score').textContent = value;
                            }

                            function bootstrap() {
                              updateScore('2');
                            }

                            document.addEventListener('DOMContentLoaded', bootstrap);
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!DOCTYPE html>
                <html>
                <body>
                  <main id="app-root">
                    <div id="score">0</div>
                  </main>
                  <script id="app-script">
                    function bootstrap() {
                    }

                    document.addEventListener('DOMContentLoaded', bootstrap);
                  </script>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.INCREMENTAL, true);
        executionState.recordPatchProgress(new FilePatchProgressState(
                Path.of("index.html"),
                FileEditStrategyNames.INLINE_SCRIPT_WORKSET,
                """
                function staleHelper() {
                  return 'stale';
                }

                function bootstrap() {
                  staleHelper();
                }
                """,
                List.of(new EditUnit(
                        EditUnitKind.INLINE_SCRIPT_SYMBOL_BATCH,
                        "index.html.inline.js#inline-unit-append",
                        List.of(),
                        1
                ))
        ));

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：继续补齐当前入口中的脚本逻辑",
                new Subtask(
                        "补齐脚本逻辑",
                        "继续补齐当前入口中的脚本逻辑",
                        List.of(),
                        List.of("页面初始化后应更新分数"),
                        List.of(),
                        List.of("页面可加载"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐脚本逻辑"))
                ),
                null,
                "",
                "补齐脚本逻辑",
                executionState
        );

        assertFalse(inlineScriptWorksetCalled.get(), "失配的旧 progress 不应再把执行链拉回 inline-script-workset");
        assertTrue(focusedScriptCalled.get(), "单入口脚本在丢弃失配 progress 后应直接走 focused script region");
        assertFalse(generated.contains("staleHelper"), "丢弃旧 progress 后不应把旧骨架残留继续带入最终结果");
        assertTrue(generated.contains("updateScore('2');"));
    }

    @Test
    void multiSymbolInlineScriptStillUsesWorkingSetPath() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean preciseHtmlCalled = new AtomicBoolean(false);
        AtomicBoolean focusedScriptCalled = new AtomicBoolean(false);
        AtomicBoolean inlineScriptWorksetCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("当前 HTML 入口文件里的主脚本已被抽成独立代码工作集")) {
                    inlineScriptWorksetCalled.set(true);
                    assertTrue(userPrompt.contains("- allowedSymbols: bindButton, bootstrap"));
                    return StructuredDiffTestSupport.replaceRangeStartingAt(
                            userPrompt,
                            "function bootstrap() {",
                            2,
                            "function bootstrap() {",
                            "  bindButton();",
                            "}"
                    );
                }
                if (systemPrompt.contains("script id=\"app-script\"")) {
                    focusedScriptCalled.set(true);
                    return "";
                }
                if (systemPrompt.contains("精确改写")) {
                    preciseHtmlCalled.set(true);
                    return "";
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        Files.writeString(tempDir.resolve("index.html"), """
                <!DOCTYPE html>
                <html>
                <body>
                  <main id="app-root">
                    <button id="start-btn">开始</button>
                  </main>
                  <script id="app-script">
                    function bindButton() {
                      document.getElementById('start-btn')?.addEventListener('click', bootstrap);
                    }

                    function bootstrap() {
                    }

                    document.addEventListener('DOMContentLoaded', bootstrap);
                  </script>
                </body>
                </html>
                """);

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        String generated = invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：补齐现有入口中的脚本逻辑",
                new Subtask(
                        "补齐脚本逻辑",
                        "补齐现有入口中的脚本逻辑",
                        List.of(),
                        List.of("按钮绑定完成"),
                        List.of(),
                        List.of("页面可加载"),
                        false,
                        DeliveryMode.INCREMENTAL,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐脚本逻辑"))
                ),
                null,
                "",
                "补齐脚本逻辑",
                new SubtaskExecutionState(DeliveryMode.INCREMENTAL, false)
        );

        assertTrue(inlineScriptWorksetCalled.get(), "多符号脚本仍应走 inline-script-workset");
        assertFalse(focusedScriptCalled.get(), "多符号脚本不应被误收窄到 focused script region");
        assertFalse(preciseHtmlCalled.get(), "多符号脚本不应先回到宿主级 precise-html");
        assertTrue(generated.contains("bindButton();"));
    }

    @Test
    void structuredHtmlSkeletonPromptRequiresNamedTopLevelScriptScaffold() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> capturedSystemPrompt = new AtomicReference<>("");
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("结构化页面草稿")) {
                    capturedSystemPrompt.set(systemPrompt);
                    return """
                            {
                              "documentTitle": "俄罗斯方块",
                              "headHtml": null,
                              "markupHtml": "<div id=\\\"app\\\"></div>",
                              "styleCss": "body { margin: 0; }",
                              "scriptJs": "function bootstrap() {\\n}\\n\\ndocument.addEventListener('DOMContentLoaded', bootstrap);",
                              "bodyAppendHtml": null
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        FileEditCoordinator coordinator = new FileEditCoordinator(
                provider,
                workspace,
                objectMapper,
                new devflow.agent.parsing.TreeSitterSupport(),
                new devflow.agent.editing.HtmlPreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.CodePreciseEditor(new devflow.agent.parsing.TreeSitterSupport()),
                new devflow.agent.editing.HtmlDocumentAssembler(),
                new GenerationEngine(),
                new RuntimeWorkingSetResolver(),
                2
        );

        invokeGenerateFileContent(
                coordinator,
                Path.of("index.html"),
                "计划：建立最小可运行入口",
                new Subtask(
                        "建立入口",
                        "创建最小可运行游戏入口",
                        List.of(),
                        List.of("入口文件可打开"),
                        List.of(),
                        List.of("页面可加载"),
                        true,
                        DeliveryMode.SKELETON,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "建立入口"))
                ),
                null,
                "",
                "建立入口",
                new SubtaskExecutionState(DeliveryMode.SKELETON, false)
        );

        assertTrue(capturedSystemPrompt.get().contains("命名的顶层函数/类"), "结构化 HTML 骨架应优先生成命名脚本骨架，避免匿名大回调");
    }

    @Test
    void focusedScriptRegionUnwrapsSingleScriptWrapper() throws Exception {
        FocusedHtmlRegionNormalizer normalizer = new FocusedHtmlRegionNormalizer(
                new GeneratedPayloadSupport(new StructuredPayloadReader(new ObjectMapper()))
        );

        devflow.agent.editing.HtmlPrecisePatch patch = normalizer.toPatch(
                HtmlEditRegion.SCRIPT,
                """
                <script id="app-script">
                function startGame() {
                  return 1;
                }
                </script>
                """
        );

        assertEquals("""
                function startGame() {
                  return 1;
                }""", patch.scriptJs());
    }

    @Test
    void focusedScriptRegionRejectsMixedWrappedMarkup() throws Exception {
        FocusedHtmlRegionNormalizer normalizer = new FocusedHtmlRegionNormalizer(
                new GeneratedPayloadSupport(new StructuredPayloadReader(new ObjectMapper()))
        );

        devflow.agent.editing.PreciseEditException exception = assertThrows(
                devflow.agent.editing.PreciseEditException.class,
                () -> normalizer.toPatch(
                        HtmlEditRegion.SCRIPT,
                        """
                        <script>const value = 1;</script>
                        <div>extra</div>
                        """
                )
        );
        assertTrue(exception.getMessage().contains("Focused script region"));
    }

    private String extractFirstAllowedSymbol(String prompt) {
        String marker = "- allowedSymbols: ";
        int index = prompt.indexOf(marker);
        if (index < 0) {
            return "alpha";
        }
        String value = prompt.substring(index + marker.length()).split("\\R", 2)[0].trim();
        if (value.isBlank() || "(append-only)".equals(value)) {
            return "alpha";
        }
        return value.split(",")[0].trim();
    }

    private String invokeGenerateFileContent(
            FileEditCoordinator coordinator,
            Path relativePath,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String feedback,
            String reason,
            SubtaskExecutionState executionState
    ) throws Exception {
        java.lang.reflect.Method method = FileEditCoordinator.class.getDeclaredMethod(
                "generateFileOutput",
                Path.class,
                Path.class,
                String.class,
                Subtask.class,
                TaskPackage.class,
                String.class,
                String.class,
                SubtaskExecutionState.class,
                devflow.agent.context.ContractView.class,
                devflow.agent.validation.ProjectFingerprint.class,
                String.class,
                ImplementationEventJournal.class
        );
        method.setAccessible(true);
        RunRecord runRecord = runRecord("实现一个俄罗斯方块", "需要纯网页版");
        GeneratedFileOutput output = (GeneratedFileOutput) method.invoke(
                coordinator,
                tempDir,
                relativePath,
                planSummary,
                subtask,
                taskPackage,
                feedback,
                reason,
                executionState,
                null,
                null,
                "",
                new ImplementationEventJournal(null, null, tempDir, runRecord)
        );
        return output.primaryContent();
    }

    private RunRecord runRecord(String goal, String constraints) {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                goal,
                constraints,
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }

    private static class NoopLlmProvider implements LlmProvider {
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
    }
}
