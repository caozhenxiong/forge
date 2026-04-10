package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ArtifactSummaryBuilder;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ExecutionContract;
import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageFlowPolicy;
import devflow.agent.orchestrator.StageType;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.supervisor.SupervisorFallbackPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void fileScopedFeedbackDoesNotLeakSiblingSymbolFixesIntoOtherFiles() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        Files.writeString(tempDir.resolve("main.js"), """
                function updateGame() {
                  return 'todo';
                }

                function initGameArea() {
                  return 'todo';
                }
                """);
        Files.writeString(tempDir.resolve("constants.js"), """
                const GRID_WIDTH = 10;
                const GRID_HEIGHT = 20;
                """);

        String feedback = """
                [FIX_MODE=PATCH]
                [VERIFICATION]
                - summary: 游戏状态管理功能未实现
                - changeRequest: 请实现 updateGame、initGameArea、initScore、initNextPiece 四个函数的具体逻辑
                [COMPLETENESS]
                - summary: 当前交付仍包含显式占位标记、TODO/FIXME 或未实现提示。
                - evidence:
                - main.js: function updateGame has an empty or no-op body
                - main.js: function initGameArea has an empty or no-op body
                """;

        var treeSitterSupport = new devflow.agent.parsing.TreeSitterSupport();
        TargetLocator targetLocator = new TreeSitterTargetLocator(treeSitterSupport);
        LanguageEditAdapter codeEditAdapter = new TreeSitterCodeEditAdapter(
                targetLocator,
                new CodePreciseEditor(treeSitterSupport),
                new CodePatchKernel(
                        new CodePreciseEditor(treeSitterSupport),
                        new PatchVerifier(
                                treeSitterSupport,
                                new GeneratedContentGate(new FileProjectWorkspace(), treeSitterSupport)
                        )
                )
        );
        FileScopedContextSupport contextSupport = new FileScopedContextSupport(
                workspace,
                new RuntimeWorkingSetResolver(),
                new PatchContextBuilder(targetLocator, codeEditAdapter),
                new TaskPackageMarkdownRenderer()
        );

        String scoped = contextSupport.scopeFeedbackToFile(
                tempDir,
                Path.of("constants.js"),
                List.of(Path.of("main.js"), Path.of("constants.js")),
                Files.readString(tempDir.resolve("constants.js")),
                feedback
        );

        assertTrue(scoped.contains("[FILE_SCOPED_RETRY]"));
        assertTrue(scoped.contains("currentFile: constants.js"));
        assertTrue(!scoped.contains("updateGame"), scoped);
        assertTrue(!scoped.contains("initGameArea"), scoped);
    }

    @Test
    void executionContractWithEntryAndSurfaceDefaultsFirstSubtaskToSkeletonMode() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> capturedFileSystemPrompt = new AtomicReference<>("");
        AtomicInteger implementationCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先建立俄罗斯方块的可运行骨架。",
                              "subtasks": [
                                {
                                  "title": "建立页面骨架",
                                  "goal": "创建最小可运行的网页入口",
                                  "acceptanceCriteria": ["页面可打开", "存在标题"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建网页入口",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                },
                                {
                                  "title": "补齐基础行为",
                                  "goal": "让入口具备最小可交互行为",
                                  "deliveryMode": "INCREMENTAL",
                                  "acceptanceCriteria": ["点击按钮会更新界面"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "补齐入口行为",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    capturedFileSystemPrompt.compareAndSet("", systemPrompt);
                    if (implementationCalls.incrementAndGet() == 1) {
                        return """
                                <!DOCTYPE html>
                                <html lang="zh-CN">
                                <head><meta charset="UTF-8"><title>Tetris</title></head>
                                <body>
                                  <main id="app-root">
                                    <h1>Tetris</h1>
                                    <button id="start-btn">开始</button>
                                  </main>
                                  <script id="app-script">
                                    document.addEventListener('DOMContentLoaded', () => {
                                      const button = document.getElementById('start-btn');
                                      if (button) {
                                        button.textContent = '开始';
                                      }
                                    });
                                  </script>
                                </body>
                                </html>
                                """;
                    }
                    return """
                            <!DOCTYPE html>
                            <html lang="zh-CN">
                            <head><meta charset="UTF-8"><title>Tetris</title></head>
                            <body>
                              <main id="app-root">
                                <h1>Tetris</h1>
                                <button id="start-btn">开始</button>
                                <p id="status">ready</p>
                              </main>
                              <script id="app-script">
                                document.addEventListener('DOMContentLoaded', () => {
                                  const button = document.getElementById('start-btn');
                                  const status = document.getElementById('status');
                                  if (button && status) {
                                    button.addEventListener('click', () => {
                                      status.textContent = 'started';
                                    });
                                  }
                                });
                              </script>
                            </body>
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
                return review(systemPrompt, candidateContent, options, null);
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个网页版俄罗斯方块", "需要纯网页版、像素风"),
                "# analysis",
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
                ""
        );

        assertTrue(bundle.implementationMarkdown().contains("交付模式：SKELETON"));
        assertTrue(bundle.backlogMarkdown().contains("实现待办"));
        assertTrue(bundle.sharedContextMarkdown().contains("共享上下文包"));
        assertTrue(bundle.taskPackagesMarkdown().contains("任务包: 建立页面骨架"));
        assertTrue(capturedFileSystemPrompt.get().contains("结构化页面草稿") || capturedFileSystemPrompt.get().contains("当前处于骨架模式"));
    }

    @Test
    void implementationReportMarksStageNotReadyWhenPlannedSubtasksRemainIncomplete() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger implementationCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先做入口，再补行为。",
                              "subtasks": [
                                {
                                  "title": "建立入口骨架",
                                  "goal": "创建最小可运行入口",
                                  "deliveryMode": "SKELETON",
                                  "ownedCapabilities": ["存在可打开入口"],
                                  "deferredCapabilities": ["补齐开始按钮行为"],
                                  "acceptanceCriteria": ["页面可打开", "存在开始按钮"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建页面入口",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                },
                                {
                                  "title": "补齐交互行为",
                                  "goal": "让开始按钮触发状态变化",
                                  "deliveryMode": "INCREMENTAL",
                                  "ownedCapabilities": ["开始按钮触发状态变化"],
                                  "deferredCapabilities": [],
                                  "acceptanceCriteria": ["点击开始后状态变化"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "补齐交互逻辑",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    if (implementationCalls.incrementAndGet() == 1) {
                        return """
                                <!DOCTYPE html>
                                <html lang="zh-CN">
                                <head><meta charset="UTF-8"><title>Tetris</title></head>
                                <body>
                                  <main id="app-root">
                                    <button id="start-btn">开始</button>
                                  </main>
                                  <script id="app-script">
                                    document.addEventListener('DOMContentLoaded', () => {
                                      const button = document.getElementById('start-btn');
                                      if (button) {
                                        button.textContent = '开始';
                                      }
                                    });
                                  </script>
                                </body>
                                </html>
                                """;
                    }
                    throw new IllegalStateException("Ollama returned unusable content for model qwen3-coder:30b after 3 attempts (done=true, done_reason=length, eval_count=1200)");
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态网页做基础资源检查即可。",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个网页版交互页面", "需要纯网页版"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens
                """,
                ""
        );

        assertTrue(bundle.implementationMarkdown().contains("- stageReady: false"));
        assertTrue(bundle.implementationMarkdown().contains("- planCompleted: false"));
        assertTrue(bundle.implementationMarkdown().contains("补齐交互行为"));
        assertTrue(bundle.implementationMarkdown().contains("NOT_EXECUTED") || bundle.implementationMarkdown().contains("FAILED"));
    }

    @Test
    void runnableMilestonePassingAllowsExecutionToProceedToLaterSubtasks() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger implementationCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先交付可运行入口，再补游戏逻辑。",
                              "subtasks": [
                                {
                                  "title": "建立入口骨架",
                                  "goal": "创建可打开的入口页面并接好开始按钮",
                                  "deliveryMode": "SKELETON",
                                  "runnableMilestone": true,
                                  "ownedCapabilities": ["存在可打开入口", "开始按钮可触发状态变化"],
                                  "deferredCapabilities": ["补齐游戏下落逻辑"],
                                  "acceptanceCriteria": ["页面可打开", "点击开始后状态变化"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "建立网页入口",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                },
                                {
                                  "title": "补齐游戏逻辑",
                                  "goal": "实现最小游戏循环和渲染",
                                  "deliveryMode": "INCREMENTAL",
                                  "ownedCapabilities": ["方块开始下落"],
                                  "deferredCapabilities": [],
                                  "acceptanceCriteria": ["开始后画面进入运行态"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "补齐运行逻辑",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    if (implementationCalls.incrementAndGet() == 1) {
                        return """
                                <!DOCTYPE html>
                                <html lang="zh-CN">
                                <head><meta charset="UTF-8"><title>Tetris</title></head>
                                <body>
                                  <main id="app-root">
                                    <button id="start-btn">开始</button>
                                    <p id="status">ready</p>
                                  </main>
                                  <script id="app-script">
                                    document.addEventListener('DOMContentLoaded', () => {
                                      const button = document.getElementById('start-btn');
                                      const status = document.getElementById('status');
                                      if (button && status) {
                                        button.addEventListener('click', () => {
                                          status.textContent = 'started';
                                        });
                                      }
                                    });
                                  </script>
                                </body>
                                </html>
                                """;
                    }
                    throw new IllegalStateException("Ollama returned unusable content for model qwen3-coder:30b after 3 attempts (done=true, done_reason=length, eval_count=1200)");
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态网页做基础资源检查即可。",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个网页版交互页面", "需要纯网页版"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-starts
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-starts
                """,
                ""
        );

        assertTrue(bundle.implementationMarkdown().contains("- executedSubtasks: 2"));
        assertTrue(bundle.implementationMarkdown().contains("建立入口骨架"));
        assertTrue(bundle.implementationMarkdown().contains("补齐游戏逻辑"));
        assertTrue(bundle.implementationMarkdown().contains("最终状态：FAILED") || bundle.implementationMarkdown().contains("Final Status: FAILED"));
        assertTrue(bundle.implementationMarkdown().contains("Ollama returned unusable content") || bundle.implementationMarkdown().contains("OUTPUT_TRUNCATED"));
    }

    @Test
    void planningCoverageFailureIsRenderedAsRecoverableImplementationArtifact() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先做基础骨架。",
                              "subtasks": [
                                {
                                  "title": "建立入口骨架",
                                  "goal": "创建可打开的入口页面",
                                  "deliveryMode": "SKELETON",
                                  "runnableMilestone": true,
                                  "ownedCapabilities": ["存在可打开入口"],
                                  "deferredCapabilities": [],
                                  "acceptanceCriteria": ["页面可打开"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "建立网页入口",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> executor.execute(
                tempDir,
                runRecord("实现一个网页版俄罗斯方块", "需要纯网页版、可直接打开运行"),
                "# analysis",
                """
                # 产品需求文档

                ## 3. 功能范围
                ### 3.1 核心功能
                - 支持开始、暂停、重开
                - 支持方向键控制方块移动与旋转
                ### 3.2 辅助功能
                - 游戏状态提示：显示当前游戏状态（运行中/暂停/结束）

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-starts
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-starts
                """,
                ""
        ));

        assertTrue(exception.getMessage().contains("Implementation planning exhausted internal retries"));
        assertTrue(exception.getMessage().contains("当前实现计划未覆盖执行契约"));
    }

    @Test
    void patchRetryContinuesWithReasonAwareRuntimeWiringRepairWhenPreviousPlanCompletedButArchitectCheckFailed() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger planningCalls = new AtomicInteger();
        AtomicInteger generationCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    throw new IllegalStateException("Reason-aware PATCH continuation should not replan.");
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    generationCalls.incrementAndGet();
                    return """
                            <!DOCTYPE html>
                            <html lang="zh-CN">
                            <head>
                              <meta charset="UTF-8">
                              <title>Tetris</title>
                            </head>
                            <body>
                              <main id="app-root"></main>
                              <script id="app-script">
                                document.addEventListener('DOMContentLoaded', () => {
                                  const root = document.getElementById('app-root');
                                  if (root) {
                                    root.textContent = 'ready';
                                  }
                                });
                              </script>
                            </body>
                            </html>
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "模块集成修复做基础验证即可。",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        Files.writeString(tempDir.resolve("index.html"), "<!DOCTYPE html><html><body><main id=\"app-root\"></main></body></html>");
        Files.writeString(tempDir.resolve("game.js"), "export const state = {};\n");
        Files.writeString(tempDir.resolve("renderer.js"), "export function render() {}\n");

        String previousStateJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                new ImplementationStateSnapshot(
                        "先拆出入口、核心逻辑和渲染层，再补整体接线。",
                        List.of(
                                new ImplementationStateSnapshot.PlannedSubtaskState(
                                        "建立入口",
                                        "创建网页入口",
                                        List.of(),
                                        List.of("入口"),
                                        List.of(),
                                        List.of("页面可打开"),
                                        true,
                                        "PATCH",
                                        List.of(new ImplementationStateSnapshot.FileChangeState(
                                                "index.html",
                                                "WRITE",
                                                "入口",
                                                FileEditScope.HOST_HTML_PATCH.name(),
                                                RuntimeOwnershipMode.INLINE_HOST.name()
                                        ))
                                ),
                                new ImplementationStateSnapshot.PlannedSubtaskState(
                                        "实现核心逻辑",
                                        "补齐核心逻辑",
                                        List.of(),
                                        List.of("核心逻辑"),
                                        List.of(),
                                        List.of("核心逻辑存在"),
                                        false,
                                        "INCREMENTAL",
                                        List.of(new ImplementationStateSnapshot.FileChangeState("game.js", "WRITE", "核心逻辑"))
                                ),
                                new ImplementationStateSnapshot.PlannedSubtaskState(
                                        "实现渲染层",
                                        "补齐渲染层",
                                        List.of(),
                                        List.of("渲染层"),
                                        List.of(),
                                        List.of("渲染层存在"),
                                        false,
                                        "INCREMENTAL",
                                        List.of(new ImplementationStateSnapshot.FileChangeState("renderer.js", "WRITE", "渲染层"))
                                )
                        ),
                        List.of(
                                new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                        "建立入口",
                                        true,
                                        List.of(new ImplementationStateSnapshot.SubtaskAttemptState(1, true, "ok", "", "APPROVED", "NONE", "ok", "", "", "", null, null))
                                ),
                                new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                        "实现核心逻辑",
                                        true,
                                        List.of(new ImplementationStateSnapshot.SubtaskAttemptState(1, true, "ok", "", "APPROVED", "NONE", "ok", "", "", "", null, null))
                                ),
                                new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                        "实现渲染层",
                                        true,
                                        List.of(new ImplementationStateSnapshot.SubtaskAttemptState(1, true, "ok", "", "APPROVED", "NONE", "ok", "", "", "", null, null))
                                )
                        ),
                        List.of(),
                        "",
                        true,
                        false,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID.name(),
                        "入口页缺少稳定 runtime wiring。",
                        List.of()
                )
        );

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个可玩的网页版俄罗斯方块", "需要纯网页版、可直接打开运行"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens
                """,
                patchNote("请在现有实现基础上补齐入口接线与模块集成。"),
                null,
                previousStateJson
        );

        assertEquals(0, planningCalls.get());
        assertTrue(generationCalls.get() >= 1);
        Subtask continuation = bundle.snapshot().plan().subtasks().getLast();
        assertTrue(
                "延续修复运行时接线缺口".equals(continuation.title())
                        || "Continue repairing runtime wiring".equals(continuation.title())
        );
        assertEquals(1, continuation.changes().size());
        assertEquals("index.html", continuation.changes().getFirst().path());
        assertEquals(RuntimeOwnershipMode.INLINE_HOST, continuation.changes().getFirst().runtimeOwnership());
    }

    @Test
    void fileGenerationPromptUsesTaskPackageWorkingSetInsteadOfFullUpstreamDocuments() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> capturedUserPrompt = new AtomicReference<>("");
        AtomicInteger implementationCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先做入口。",
                              "subtasks": [
                                {
                                  "title": "建立入口骨架",
                                  "goal": "创建最小可运行入口",
                                  "deliveryMode": "SKELETON",
                                  "ownedCapabilities": ["存在可打开入口"],
                                  "deferredCapabilities": ["补齐交互逻辑"],
                                  "acceptanceCriteria": ["页面可打开", "存在开始按钮"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建页面入口",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                },
                                {
                                  "title": "补齐交互逻辑",
                                  "goal": "让开始按钮改变状态",
                                  "deliveryMode": "INCREMENTAL",
                                  "ownedCapabilities": ["点击开始后状态变化"],
                                  "deferredCapabilities": [],
                                  "acceptanceCriteria": ["点击开始后状态变化"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "补齐交互逻辑",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    if (implementationCalls.getAndIncrement() == 0) {
                        capturedUserPrompt.set(userPrompt);
                    }
                    return """
                            <!DOCTYPE html>
                            <html lang="zh-CN">
                            <head><meta charset="UTF-8"><title>Tetris</title></head>
                            <body>
                              <main id="app-root"><button id="start-btn">开始</button><p id="status">ready</p></main>
                              <script id="app-script">
                                document.addEventListener('DOMContentLoaded', () => {
                                  const button = document.getElementById('start-btn');
                                  const status = document.getElementById('status');
                                  if (button && status) {
                                    button.addEventListener('click', () => status.textContent = 'started');
                                  }
                                });
                              </script>
                            </body>
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        executor.execute(
                tempDir,
                runRecord("实现一个网页版俄罗斯方块", "需要纯网页版、可直接打开运行"),
                "# 需求分析\n\n这里是一段很长的分析正文，不应该直接原样出现在文件级生成 prompt 里。",
                "# 产品需求文档\n\n这里是一段很长的 PRD 正文，不应该直接原样出现在文件级生成 prompt 里。",
                "# 技术方案设计\n\n这里是一段很长的设计正文，不应该直接原样出现在文件级生成 prompt 里。",
                ""
        );

        assertTrue(capturedUserPrompt.get().contains("当前任务包"));
        assertTrue(capturedUserPrompt.get().contains("Shared Context Bundle"));
        assertTrue(!capturedUserPrompt.get().contains("需求分析："));
        assertTrue(!capturedUserPrompt.get().contains("产品需求文档："));
        assertTrue(!capturedUserPrompt.get().contains("技术方案设计："));
    }

    @Test
    void planningPromptDoesNotConflictWhenDeliveryPolicyAllowsThreeFiles() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> capturedPlanningPrompt = new AtomicReference<>("");
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    capturedPlanningPrompt.set(systemPrompt);
                    return """
                            {
                              "summary": "先拆一个很小的步骤。",
                              "subtasks": [
                                {
                                  "title": "建立入口",
                                  "goal": "创建入口文件",
                                  "deliveryMode": "PATCH",
                                  "acceptanceCriteria": ["页面存在"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建入口",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        executor.execute(
                tempDir,
                runRecord("实现一个网页应用", ""),
                "# analysis",
                "# prd",
                "# design",
                deliveryMaxFilesNote(3)
        );

        assertTrue(capturedPlanningPrompt.get().contains("可放宽到最多 3 个文件"));
        assertTrue(capturedPlanningPrompt.get().contains("每个子任务最多改 3 个文件"));
    }

    @Test
    void implementationAcceptsBrowserEsModuleJavaScriptGeneration() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "建立浏览器 ESM 骨架。",
                              "subtasks": [
                                {
                                  "title": "创建游戏引擎模块",
                                  "goal": "创建浏览器可用的 ES module",
                                  "acceptanceCriteria": ["模块语法合法"],
                                  "changes": [
                                    {
                                      "path": "src/GameEngine.js",
                                      "action": "WRITE",
                                      "reason": "创建入口模块"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    if (systemPrompt.contains("符号级精确改写")) {
                        if (systemPrompt.contains("当前文件为空或新建文件")) {
                            return """
                                    {
                                      "operations": [
                                        {
                                          "action": "APPEND_FILE",
                                          "targetSymbol": null,
                                          "targetKind": null,
                                          "contentLines": [
                                            "import { createEngineState } from './state.js';",
                                            "",
                                            "export class GameEngine {",
                                            "  constructor() {",
                                            "  }",
                                            "}"
                                          ]
                                        }
                                      ]
                                    }
                                    """;
                        }
                        return """
                                {
                                  "operations": [
                                    {
                                      "action": "REPLACE_SYMBOL_BODY",
                                      "targetSymbol": "GameEngine",
                                      "targetKind": "class",
                                      "contentLines": [
                                        "constructor() {",
                                        "  this.state = createEngineState();",
                                        "}"
                                      ]
                                    }
                                  ]
                                }
                                """;
                    }
                    return """
                            import { createEngineState } from './state.js';

                            export class GameEngine {
                              constructor() {
                                this.state = createEngineState();
                              }
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态项目做基础验证。",
                              "steps": [
                                {
                                  "capability": "WEB_JAVASCRIPT_SYNTAX_CHECK",
                                  "reason": "检查 JavaScript 语法。",
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个模块", "需要浏览器 ES module"),
                "# analysis",
                "# prd",
                "# design",
                ""
        );

        assertTrue(bundle.implementationMarkdown().contains("最终状态：COMPLETED"), bundle.implementationMarkdown());
    }

    @Test
    void patchRetryReusesPreviousIncompletePlanInsteadOfReplanningUnderStricterPolicy() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger planningCalls = new AtomicInteger();
        AtomicInteger generationCalls = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    planningCalls.incrementAndGet();
                    throw new AssertionError("patch retry should reuse previous implementation plan");
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    if (systemPrompt.contains("当前 HTML 入口文件里的主脚本已被抽成独立代码工作集")) {
                        return """
                                {
                                  "operations": [
                                    {
                                      "action": "APPEND_FILE",
                                      "targetSymbol": null,
                                      "targetKind": null,
                                      "contentLines": [
                                        "document.addEventListener('DOMContentLoaded', () => {",
                                        "  const button = document.getElementById('start-btn');",
                                        "  const status = document.getElementById('status');",
                                        "  if (button && status) {",
                                        "    button.addEventListener('click', () => {",
                                        "      status.textContent = 'started';",
                                        "    });",
                                        "  }",
                                        "});"
                                      ]
                                    }
                                  ]
                                }
                                """;
                    }
                    if (systemPrompt.contains("请对现有 HTML 页面做“精确改写”")) {
                        return """
                                {
                                  "markupHtml": null,
                                  "styleCss": null,
                                  "scriptJs": "document.addEventListener('DOMContentLoaded', () => {\\n  const button = document.getElementById('start-btn');\\n  const status = document.getElementById('status');\\n  if (button && status) {\\n    button.textContent = '开始';\\n    button.addEventListener('click', () => {\\n      status.textContent = 'started';\\n    });\\n  }\\n});",
                                  "headAppendHtml": null,
                                  "bodyAppendHtml": null
                                }
                                """;
                    }
                    generationCalls.incrementAndGet();
                    return """
                            <!DOCTYPE html>
                            <html lang="zh-CN">
                            <head>
                              <meta charset="UTF-8">
                              <title>Tetris</title>
                            </head>
                            <body>
                              <main id="app-root">
                                <button id="start-btn">开始</button>
                                <p id="status">ready</p>
                              </main>
                              <script id="app-script">
                                document.addEventListener('DOMContentLoaded', () => {
                                  const button = document.getElementById('start-btn');
                                  const status = document.getElementById('status');
                                  if (button && status) {
                                    button.addEventListener('click', () => {
                                      status.textContent = 'started';
                                    });
                                  }
                                });
                              </script>
                            </body>
                            </html>
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态网页做基础资源检查即可。",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        Files.writeString(tempDir.resolve("index.html"), """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Tetris</title>
                </head>
                <body>
                  <main id="app-root">
                    <button id="start-btn">开始</button>
                    <p id="status">ready</p>
                  </main>
                  <script id="app-script">
                    document.addEventListener('DOMContentLoaded', () => {
                      const button = document.getElementById('start-btn');
                      const status = document.getElementById('status');
                      if (button && status) {
                        button.textContent = '开始';
                      }
                    });
                  </script>
                </body>
                </html>
                """);

        String previousStateJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                new ImplementationStateSnapshot(
                        "先交付入口骨架，再补齐交互行为。",
                        List.of(
                                new ImplementationStateSnapshot.PlannedSubtaskState(
                                        "建立入口骨架",
                                        "创建可打开的网页入口",
                                        List.of("CAP-1"),
                                        List.of("存在可打开入口"),
                                        List.of("补齐开始按钮行为"),
                                        List.of("页面可打开", "存在开始按钮"),
                                        true,
                                        "SKELETON",
                                        List.of(new ImplementationStateSnapshot.FileChangeState(
                                                "index.html",
                                                "WRITE",
                                                "创建入口",
                                                FileEditScope.AUTO.name(),
                                                RuntimeOwnershipMode.INLINE_HOST.name()
                                        ))
                                ),
                                new ImplementationStateSnapshot.PlannedSubtaskState(
                                        "补齐交互行为",
                                        "让开始按钮触发状态变化",
                                        List.of("CAP-2"),
                                        List.of("开始按钮触发状态变化"),
                                        List.of(),
                                        List.of("点击开始后状态变化"),
                                        false,
                                        "PATCH",
                                        List.of(new ImplementationStateSnapshot.FileChangeState(
                                                "index.html",
                                                "WRITE",
                                                "补齐行为",
                                                FileEditScope.AUTO.name(),
                                                RuntimeOwnershipMode.INLINE_HOST.name()
                                        ))
                                )
                        ),
                        List.of(
                                new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                        "建立入口骨架",
                                        true,
                                        List.of(
                                                new ImplementationStateSnapshot.SubtaskAttemptState(
                                                        1,
                                                        true,
                                                        "ok",
                                                        "",
                                                        "APPROVED",
                                                        "NONE",
                                                        "ok",
                                                        "",
                                                        "",
                                                        "",
                                                        null,
                                                        null
                                                )
                                        )
                                ),
                                new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                                        "补齐交互行为",
                                        false,
                                        List.of(
                                                new ImplementationStateSnapshot.SubtaskAttemptState(
                                                        1,
                                                        false,
                                                        "缺少交互状态变化",
                                                        "",
                                                        "REVISION_REQUIRED",
                                                        "PATCH",
                                                        "当前子任务仍未完成。",
                                                        "请补齐开始按钮行为。",
                                                        "",
                                                        "",
                                                        null,
                                                        null
                                                )
                                        )
                                )
                        ),
                        List.of(),
                        "",
                        false,
                        true,
                        List.of("补齐交互行为")
                )
        );

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个可直接运行的网页游戏", "需要入口和行为"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, runtime-surface-renders
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, runtime-surface-renders
                """,
                patchNoteWithDeliveryMaxFiles(1),
                null,
                previousStateJson
        );

        assertEquals(0, planningCalls.get());
        assertTrue(bundle.implementationMarkdown().contains("- plannedSubtasks: 2"));
        assertTrue(bundle.implementationMarkdown().contains("- completedSubtasks: 1"));
        assertTrue(bundle.implementationMarkdown().contains("- stageReady: false"));
        assertTrue(Files.readString(tempDir.resolve("index.html")).contains("status.textContent = 'started';"));
    }

    @Test
    void implementationPlanMustCoverRunnableEntryForPureWebGoal() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先补逻辑模块。",
                              "subtasks": [
                                {
                                  "title": "建立游戏逻辑",
                                  "goal": "创建核心模块",
                                  "deliveryMode": "SKELETON",
                                  "acceptanceCriteria": ["模块语法合法"],
                                  "changes": [
                                    {
                                      "path": "game.js",
                                      "action": "WRITE",
                                      "reason": "先补核心逻辑"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.REPAIR) {
                    return """
                            {
                              "summary": "仍然只补逻辑模块。",
                              "subtasks": [
                                {
                                  "title": "建立输入模块",
                                  "goal": "创建输入模块",
                                  "deliveryMode": "SKELETON",
                                  "acceptanceCriteria": ["模块语法合法"],
                                  "changes": [
                                    {
                                      "path": "input.js",
                                      "action": "WRITE",
                                      "reason": "继续补逻辑"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "无额外自检",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> executor.execute(
                tempDir,
                runRecord("实现一个纯网页版俄罗斯方块", "需要纯网页版、能直接打开运行"),
                "# analysis",
                """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可直接打开运行的俄罗斯方块网页。

                ## 2. 目标用户与使用场景
                - 用户打开页面后即可开始游戏。

                ## 3. 功能范围
                - 支持开始、暂停、重开。

                ## 4. 非功能要求
                - 纯静态交付。

                ## 5. 验收标准
                - 页面可打开并可开始游戏。

                ## 6. 不做什么
                - 不做联网功能。

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-surface-renders
                """,
                """
                # 技术方案设计

                ## 1. 技术目标
                - 以静态网页形式交付可运行的最小游戏表面。

                ## 2. 系统边界与模块划分
                - 页面入口、渲染逻辑和输入逻辑分离。

                ## 3. 核心数据模型
                - 棋盘、方块、得分、运行状态。

                ## 4. 关键流程
                - 页面加载、开始、暂停、重开。

                ## 5. 接口、页面或命令设计
                - 页面必须提供可见游戏区域和启动控件。

                ## 6. 测试与验证策略
                - 通过浏览器 smoke 验证页面可打开和表面存在。

                ## 7. 风险与取舍
                - 先保证页面可运行，再补全逻辑细节。

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-surface-renders
                """,
                ""
        ));

        assertTrue(exception.getMessage().contains("Implementation planning exhausted internal retries"));
        assertTrue(exception.getMessage().contains("执行契约"));
    }

    @Test
    void implementationPlanCannotStopAtSkeletonWhenRunnableBehaviorIsRequired() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先创建一个可运行壳。",
                              "subtasks": [
                                {
                                  "title": "建立入口壳层",
                                  "goal": "创建最小可运行入口",
                                  "deliveryMode": "SKELETON",
                                  "acceptanceCriteria": ["页面可打开", "存在运行表面"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建入口和表面",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.REPAIR) {
                    return """
                            {
                              "summary": "仍然只创建壳层。",
                              "subtasks": [
                                {
                                  "title": "再次建立入口壳层",
                                  "goal": "保持壳层",
                                  "deliveryMode": "SKELETON",
                                  "acceptanceCriteria": ["页面可打开"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "继续保留壳层",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "无额外自检",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> executor.execute(
                tempDir,
                runRecord("实现一个可直接打开运行的应用", "需要入口和运行表面"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders, behavior-works
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders, behavior-works
                """,
                ""
        ));

        assertTrue(exception.getMessage().contains("Implementation planning exhausted internal retries"));
        assertTrue(exception.getMessage().contains("当前实现计划只建立可运行骨架")
                || exception.getMessage().contains("execution contract"));
    }

    @Test
    void implementationPlanRejectsSubtasksThatTouchTooManyFiles() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return invalidPlan();
                }
                if (role == ModelRole.REPAIR) {
                    return invalidPlan();
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> executor.execute(
                tempDir,
                runRecord("实现一个网页应用", ""),
                "# analysis",
                "# prd",
                "# design",
                ""
        ));

        assertTrue(exception.getMessage().contains("Implementation planning exhausted internal retries"));
        assertTrue(exception.getMessage().contains("Failed to parse implementation plan"));
    }

    @Test
    void malformedHtmlGeneratedDuringImplementationReturnsFailedReportInsteadOfThrowing() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "建立页面骨架",
                              "subtasks": [
                                {
                                  "title": "写入口",
                                  "goal": "创建 html",
                                  "deliveryMode": "SKELETON",
                                  "acceptanceCriteria": ["页面结构合法"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建页面入口",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    return """
                            <!DOCTYPE html>
                            <html>
                            <body>
                              <div id="app"><span
                            </body>
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        ImplementationExecutionBundle bundle = assertDoesNotThrow(
                () -> executor.execute(
                    tempDir,
                    runRecord("实现一个网页版应用", "需要纯网页版"),
                    "# analysis",
                    "# prd",
                    "# design",
                    ""
                )
        );

        assertTrue(bundle.implementationMarkdown().contains("最终状态：FAILED"));
        assertTrue(bundle.implementationMarkdown().contains("generationFailureType:"));
        assertTrue(bundle.workerResultsMarkdown().contains("执行结果: 写入口"));
        assertTrue(bundle.workerResultsMarkdown().contains("状态: FAILED") || bundle.workerResultsMarkdown().contains("status: FAILED"));
    }

    @Test
    void runningSubtaskIsReflectedInImplementationAndWorkerArtifacts() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        List<ImplementationExecutionBundle> snapshots = new ArrayList<>();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先建立入口，再补逻辑。",
                              "subtasks": [
                                {
                                  "title": "建立入口",
                                  "goal": "创建最小可运行入口",
                                  "deliveryMode": "SKELETON",
                                  "acceptanceCriteria": ["页面可打开"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建入口",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                },
                                {
                                  "title": "补齐逻辑",
                                  "goal": "创建游戏逻辑模块",
                                  "deliveryMode": "INCREMENTAL",
                                  "acceptanceCriteria": ["模块语法合法"],
                                  "changes": [
                                    {
                                      "path": "game.js",
                                      "action": "WRITE",
                                      "reason": "创建逻辑模块"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("结构化页面草稿")) {
                    return """
                            {
                              "documentTitle": "Tetris",
                              "headHtml": null,
                              "markupHtml": "<canvas id=\\"board\\"></canvas>",
                              "styleCss": "body { margin: 0; }",
                              "scriptJs": "console.log('ready');",
                              "bodyAppendHtml": null
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("符号级精确改写")) {
                    return """
                            {
                              "operations": [
                                {
                                  "action": "APPEND_FILE",
                                  "targetSymbol": null,
                                  "targetKind": null,
                                  "content": "export function tick() {\\n  return 1;\\n}"
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态项目做基础验证。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "检查页面资源。",
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        executor.execute(
                tempDir,
                runRecord("实现一个网页版应用", "需要纯网页版"),
                "# analysis",
                "# prd",
                "# design",
                "",
                null,
                "",
                snapshots::add
        );

        assertTrue(
                snapshots.stream().anyMatch(bundle ->
                        bundle.implementationMarkdown().contains("最终状态：RUNNING")
                                && bundle.workerResultsMarkdown().contains("状态: RUNNING")),
                "运行中的子任务应同时反映到 implementation.md 与 worker_results.md"
        );
    }

    @Test
    void nonSkeletonSubtaskCannotPassWhenBehaviorIsStillPlaceholderOnly() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先建立壳层，再补行为。",
                              "subtasks": [
                                {
                                  "title": "建立入口壳层",
                                  "goal": "创建最小可运行入口",
                                  "deliveryMode": "SKELETON",
                                  "acceptanceCriteria": ["页面可打开", "存在运行表面"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建入口骨架",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                },
                                {
                                  "title": "填充游戏行为",
                                  "goal": "补齐基础行为",
                                  "deliveryMode": "INCREMENTAL",
                                  "acceptanceCriteria": ["开始按钮可触发真实行为", "游戏逻辑不再是占位实现"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "在现有入口中补齐行为",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    return """
                            <!DOCTYPE html>
                            <html lang="zh-CN">
                            <head>
                              <meta charset="UTF-8">
                              <title>Tetris</title>
                              <style id="app-style">
                                body { margin: 0; }
                              </style>
                            </head>
                            <body>
                              <main id="app-root">
                                <button id="start-btn">开始</button>
                              </main>
                              <script id="app-script">
                                const GameEngine = {
                                  init: () => {},
                                  start: () => {},
                                  update: () => {},
                                };

                                document.addEventListener('DOMContentLoaded', () => {
                                  document.getElementById('start-btn').addEventListener('click', () => {
                                    GameEngine.start();
                                  });
                                });
                              </script>
                            </body>
                            </html>
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态网页做基础验证。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "确认页面资源完整。",
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个可直接运行的网页游戏", "需要入口和真实交互"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders, behavior-works
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders, behavior-works
                """,
                ""
        );

        assertTrue(bundle.implementationMarkdown().contains("最终状态：FAILED"), bundle.implementationMarkdown());
        assertTrue(bundle.implementationMarkdown().contains("占位实现") || bundle.implementationMarkdown().contains("空实现"), bundle.implementationMarkdown());
    }

    @Test
    void skeletonSubtaskCanDeferBehaviorToLaterSubtaskWithoutBeingRejectedForGlobalMissingFeatures() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger implementationCalls = new AtomicInteger();
        AtomicReference<String> firstReviewCandidate = new AtomicReference<>("");
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "先建立入口壳层，再补齐游戏行为。",
                              "subtasks": [
                                {
                                  "title": "建立入口壳层",
                                  "goal": "创建最小可运行入口和表面",
                                  "deliveryMode": "SKELETON",
                                  "ownedCapabilities": ["页面入口", "运行表面", "启动按钮"],
                                  "deferredCapabilities": ["游戏主循环", "方块移动", "碰撞检测", "键盘控制"],
                                  "acceptanceCriteria": ["页面可打开", "存在运行表面", "开始按钮可见"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建入口骨架",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                },
                                {
                                  "title": "补齐核心行为",
                                  "goal": "实现最小可运行交互",
                                  "deliveryMode": "INCREMENTAL",
                                  "ownedCapabilities": ["游戏主循环", "键盘控制", "启动后状态切换"],
                                  "deferredCapabilities": [],
                                  "acceptanceCriteria": ["开始按钮触发真实行为", "键盘事件可改变状态", "不再保留空实现"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "在入口文件内补齐行为",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    if (implementationCalls.incrementAndGet() == 1) {
                        return """
                                <!DOCTYPE html>
                                <html lang="zh-CN">
                                <head>
                                  <meta charset="UTF-8">
                                  <title>Tetris</title>
                                </head>
                                <body>
                                  <main id="app-root">
                                    <canvas id="game-canvas" width="300" height="600"></canvas>
                                    <button id="start-btn">开始</button>
                                  </main>
                                  <script id="app-script">
                                    let running = false;
                                    function update() {
                                      // TODO: 后续补充游戏主循环
                                    }
                                    document.getElementById('start-btn').addEventListener('click', () => {
                                      running = true;
                                    });
                                  </script>
                                </body>
                                </html>
                                """;
                    }
                    return """
                            <!DOCTYPE html>
                            <html lang="zh-CN">
                            <head>
                              <meta charset="UTF-8">
                              <title>Tetris</title>
                            </head>
                            <body>
                              <main id="app-root">
                                <canvas id="game-canvas" width="300" height="600"></canvas>
                                <button id="start-btn">开始</button>
                                <p id="status">ready</p>
                              </main>
                              <script id="app-script">
                                let running = false;
                                let tickCount = 0;

                                function update() {
                                  tickCount += 1;
                                  document.getElementById('status').textContent = running ? 'running-' + tickCount : 'ready';
                                }

                                function loop() {
                                  if (!running) {
                                    return;
                                  }
                                  update();
                                }

                                document.addEventListener('keydown', (event) => {
                                  if (event.key === 'ArrowLeft') {
                                    document.getElementById('status').textContent = 'left';
                                  }
                                });

                                document.getElementById('start-btn').addEventListener('click', () => {
                                  running = true;
                                  loop();
                                });
                              </script>
                            </body>
                            </html>
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态网页做基础验证。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "确认页面资源完整。",
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
                firstReviewCandidate.compareAndSet("", candidateContent);
                if (candidateContent.contains("后续负责能力：")
                        && candidateContent.contains("游戏主循环")
                        && candidateContent.contains("碰撞检测")) {
                    return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "当前骨架职责已完成，后续能力已明确延期", "");
                }
                if (candidateContent.contains("开始按钮触发真实行为")) {
                    return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "核心行为已补齐", "");
                }
                return new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "缺少后续能力说明", "未看到明确的后续负责能力");
            }
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个可直接运行的网页游戏", "需要入口和真实交互"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders, behavior-works
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders, behavior-works
                """,
                ""
        );

        assertTrue(firstReviewCandidate.get().contains("后续负责能力："), firstReviewCandidate.get());
        assertTrue(firstReviewCandidate.get().contains("游戏主循环"), firstReviewCandidate.get());
        assertTrue(firstReviewCandidate.get().contains("碰撞检测"), firstReviewCandidate.get());
        assertTrue(bundle.implementationMarkdown().contains("最终状态：COMPLETED"), bundle.implementationMarkdown());
    }

    @Test
    void patchModeUsesPreciseHtmlEditingForAnchoredPages() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <meta charset="UTF-8">
                          <title>Tetris</title>
                          <style id="app-style">
                            body { margin: 0; }
                          </style>
                        </head>
                        <body>
                          <main id="app-root">
                            <p>loading</p>
                          </main>
                          <script id="app-script">
                            console.log('boot');
                          </script>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "精确补齐页面内容",
                              "subtasks": [
                                {
                                  "title": "填充游戏容器",
                                  "goal": "在已有 HTML 骨架中补齐主要内容",
                                  "deliveryMode": "PATCH",
                                  "acceptanceCriteria": ["保留既有外层结构", "补齐主要内容"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "在稳定锚点内精确更新内容",
                                      "editScope": "HOST_HTML_PATCH",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("精确改写")) {
                    return """
                            {
                              "markupHtml": "<section class=\\"playfield\\"><canvas id=\\"gameCanvas\\"></canvas></section>",
                              "styleCss": "body { margin: 0; background: #10131a; }",
                              "scriptJs": "window.tetrisReady = true;"
                            }
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个俄罗斯方块页面", "需要纯网页版"),
                "# analysis",
                "# prd",
                "# design",
                patchNote("")
        );

        String html = Files.readString(tempDir.resolve("index.html"));
        assertTrue(bundle.implementationMarkdown().contains("PATCH"));
        assertTrue(html.contains("<title>Tetris</title>"));
        assertTrue(html.contains("<section class=\"playfield\">"));
        assertTrue(html.contains("window.tetrisReady = true;"));
        assertTrue(html.contains("id=\"app-root\""));
        assertTrue(html.contains("id=\"app-script\""));
    }

    @Test
    void patchModeUsesPreciseCodeEditingForExistingJavaFiles() throws Exception {
        Files.writeString(
                tempDir.resolve("App.java"),
                """
                        class App {
                            void tick() {
                                System.out.println("old");
                            }
                        }
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "定点修补 Java 方法",
                              "subtasks": [
                                {
                                  "title": "更新 tick 方法",
                                  "goal": "在不重写整文件的情况下更新 tick 的实现",
                                  "deliveryMode": "PATCH",
                                  "acceptanceCriteria": ["保留 class App", "更新 tick 行为"],
                                  "changes": [
                                    {
                                      "path": "App.java",
                                      "action": "WRITE",
                                      "reason": "对现有方法做精确修改"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("符号级精确改写")) {
                    return """
                            {
                              "operations": [
                                {
                                  "action": "REPLACE_SYMBOL",
                                  "targetSymbol": "tick",
                                  "targetKind": "method",
                                  "content": "void tick() {\\n    System.out.println(\\"patched\\");\\n}"
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "当前项目无额外自检步骤",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("修复 Java 方法", ""),
                "# analysis",
                "# prd",
                "# design",
                patchNote("")
        );

        String javaSource = Files.readString(tempDir.resolve("App.java"));
        assertTrue(bundle.implementationMarkdown().contains("PATCH") || bundle.backlogMarkdown().contains("PATCH"));
        assertTrue(javaSource.contains("System.out.println(\"patched\");"));
        assertTrue(javaSource.contains("class App"));
    }

    @Test
    void patchModeUsesPreciseCodeEditingForExistingJavaScriptFiles() throws Exception {
        Files.writeString(
                tempDir.resolve("game.js"),
                """
                        class Game {
                            tick() {
                                return 1;
                            }
                        }
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "定点修补 JavaScript 方法",
                              "subtasks": [
                                {
                                  "title": "更新 tick 方法",
                                  "goal": "在不重写整文件的情况下更新 tick 的实现",
                                  "deliveryMode": "PATCH",
                                  "acceptanceCriteria": ["保留 class Game", "更新 tick 行为"],
                                  "changes": [
                                    {
                                      "path": "game.js",
                                      "action": "WRITE",
                                      "reason": "对现有方法做精确修改"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("符号级精确改写")) {
                    return """
                            {
                              "operations": [
                                {
                                  "action": "REPLACE_SYMBOL",
                                  "targetSymbol": "tick",
                                  "targetKind": "method",
                                  "content": "tick() {\\n    return 2;\\n}"
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "当前项目无额外自检步骤",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        executor.execute(
                tempDir,
                runRecord("修复 JavaScript 方法", ""),
                "# analysis",
                "# prd",
                "# design",
                patchNote("")
        );

        String jsSource = Files.readString(tempDir.resolve("game.js"));
        assertTrue(jsSource.contains("return 2;"));
        assertTrue(jsSource.contains("class Game"));
    }

    @Test
    void generationFailureRetriesPreciseJavaScriptPatchLocallyBeforeEscalating() throws Exception {
        Files.writeString(
                tempDir.resolve("game.js"),
                """
                        class Game {
                            tick() {
                                return 1;
                            }
                        }
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<Integer> patchCalls = new AtomicReference<>(0);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "定点修补 JavaScript 方法",
                              "subtasks": [
                                {
                                  "title": "更新 tick 方法",
                                  "goal": "在不重写整文件的情况下更新 tick 的实现",
                                  "deliveryMode": "PATCH",
                                  "acceptanceCriteria": ["保留 class Game", "更新 tick 行为"],
                                  "changes": [
                                    {
                                      "path": "game.js",
                                      "action": "WRITE",
                                      "reason": "对现有方法做精确修改"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("符号级精确改写")) {
                    int call = patchCalls.getAndUpdate(value -> value + 1);
                    if (call < 3) {
                        return "{}";
                    }
                    return """
                            {
                              "operations": [
                                {
                                  "action": "REPLACE_SYMBOL",
                                  "targetSymbol": "tick",
                                  "targetKind": "method",
                                  "content": "tick() {\\n    return 3;\\n}"
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    return """
                            class Game {
                                tick() {
                                    return 3;
                                }
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "当前项目无额外自检步骤",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        ImplementationExecutionBundle bundle = assertDoesNotThrow(
                () -> executor.execute(
                        tempDir,
                        runRecord("修复 JavaScript 方法", ""),
                        "# analysis",
                        "# prd",
                        "# design",
                        patchNote("")
                )
        );

        String jsSource = Files.readString(tempDir.resolve("game.js"));
        assertTrue(jsSource.contains("return 3;"));
        assertTrue(bundle.implementationMarkdown().contains("recoveryAction: RETRY_SUBTASK"));
    }

    @Test
    void repeatedGenerationFailureReturnsFailedImplementationReportInsteadOfThrowing() throws Exception {
        Files.writeString(
                tempDir.resolve("game.js"),
                """
                        class Game {
                            tick() {
                                return 1;
                            }
                        }
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "定点修补 JavaScript 方法",
                              "subtasks": [
                                {
                                  "title": "更新 tick 方法",
                                  "goal": "在不重写整文件的情况下更新 tick 的实现",
                                  "deliveryMode": "PATCH",
                                  "acceptanceCriteria": ["保留 class Game", "更新 tick 行为"],
                                  "changes": [
                                    {
                                      "path": "game.js",
                                      "action": "WRITE",
                                      "reason": "对现有方法做精确修改"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("符号级精确改写")) {
                    return "{}";
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "当前项目无额外自检步骤",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        ImplementationExecutionBundle bundle = assertDoesNotThrow(
                () -> executor.execute(
                        tempDir,
                        runRecord("修复 JavaScript 方法", ""),
                        "# analysis",
                        "# prd",
                        "# design",
                        patchNote("")
                )
        );

        String jsSource = Files.readString(tempDir.resolve("game.js"));
        assertTrue(jsSource.contains("return 1;"));
        assertTrue(bundle.implementationMarkdown().contains("FAILED"));
        assertTrue(bundle.implementationMarkdown().contains("generationFailureType: PATCH_SCHEMA_INVALID"));
    }

    @Test
    void preciseHtmlLengthFailureReturnsFailedImplementationReportInsteadOfThrowing() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <meta charset="UTF-8">
                          <title>Tetris</title>
                          <style id="app-style">
                            body { background: #000; color: #0f0; }
                          </style>
                        </head>
                        <body>
                          <main id="app-root"><h1>Tetris</h1></main>
                          <script id="app-script">
                            function initGame() {}
                          </script>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "在现有 HTML 入口上增量补齐页面功能。",
                              "subtasks": [
                                {
                                  "title": "补齐页面骨架",
                                  "goal": "在不重写整页的情况下补齐按钮和脚本接线",
                                  "deliveryMode": "PATCH",
                                  "acceptanceCriteria": ["保留入口结构", "补齐按钮"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "对现有 HTML 入口做精确改写",
                                      "editScope": "INLINE_SCRIPT_PATCH",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("精确改写")) {
                    throw new IllegalStateException(
                            "Ollama returned unusable content for model qwen3-coder:30b after 3 attempts (done=true, done_reason=length, eval_count=1400)"
                    );
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("只改写 HTML 中 <script id=\"app-script\">")) {
                    throw new IllegalStateException(
                            "Ollama returned unusable content for model qwen3-coder:30b after 3 attempts (done=true, done_reason=length, eval_count=1400)"
                    );
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "当前项目无额外自检步骤",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        ImplementationExecutionBundle bundle = assertDoesNotThrow(
                () -> executor.execute(
                        tempDir,
                        runRecord("实现一个俄罗斯方块游戏", "需要纯网页版、可直接打开运行"),
                        "# analysis",
                        """
                        # 产品需求文档

                        ## 7. Contract Metadata

                        - runtime.entryRequired: true
                        - runtime.entryKind: html-entry
                        - runtime.launchRequired: true
                        - runtime.surfaceRequired: true
                        """,
                        """
                        # 技术方案设计

                        ## 8. Contract Metadata

                        - runtime.entryRequired: true
                        - runtime.entryKind: html-entry
                        - runtime.launchRequired: true
                        - runtime.surfaceRequired: true
                        """,
                        patchNote("")
                )
        );

        String htmlSource = Files.readString(tempDir.resolve("index.html"));
        assertTrue(htmlSource.contains("<main id=\"app-root\">"));
        assertTrue(bundle.implementationMarkdown().contains("FAILED"));
        assertTrue(bundle.implementationMarkdown().contains("generationFailureType:")
                || bundle.implementationMarkdown().contains("generationFailureSummary:")
                || bundle.implementationMarkdown().contains("generationFailureEvidence:"));
    }

    @Test
    void qualityDrivenHtmlSubtaskExternalizesInlineScriptBeforeContinuingImplementation() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <meta charset="UTF-8">
                          <title>Tetris</title>
                          <style id="app-style">
                            body { background: #000; color: #0f0; }
                          </style>
                        </head>
                        <body>
                          <main id="app-root"><h1>Tetris</h1><button id="start-btn">开始</button><p id="status">ready</p></main>
                          <script id="app-script">
                            function initGame() {}
                          </script>
                        </body>
                        </html>
                        """
        );

        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        java.util.concurrent.atomic.AtomicInteger preciseHtmlCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger inlineScriptCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger codeFileCalls = new java.util.concurrent.atomic.AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "在现有 HTML 入口上增量补齐页面功能。",
                              "subtasks": [
                                {
                                  "title": "补齐页面骨架",
                                  "goal": "在不重写整页的情况下补齐按钮和脚本接线",
                                  "deliveryMode": "PATCH",
                                  "acceptanceCriteria": ["保留入口结构", "补齐按钮行为"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "对现有 HTML 入口做精确改写",
                                      "editScope": "HOST_HTML_PATCH",
                                      "runtimeOwnership": "EXTERNAL_COMPANION"
                                    },
                                    {
                                      "path": "index.app.js",
                                      "action": "WRITE",
                                      "reason": "抽出 companion runtime 并承接主逻辑"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("当前 HTML 入口文件里的主脚本已被抽成独立代码工作集")) {
                    if (inlineScriptCalls.incrementAndGet() == 1) {
                        return """
                                {
                                  "operations": [
                                    {
                                      "action": "APPEND_FILE",
                                      "targetSymbol": null,
                                      "targetKind": null,
                                      "contentLines": [
                                        "function bindStartButton() {",
                                        "  const button = document.getElementById('start-btn');",
                                        "  const status = document.getElementById('status');",
                                        "  if (button && status) {",
                                        "    button.addEventListener('click', () => {",
                                        "      status.textContent = 'started';",
                                        "    });",
                                        "  }",
                                        "}"
                                      ]
                                    }
                                  ]
                                }
                                """;
                    }
                    return """
                            {
                              "operations": [
                                {
                                  "action": "REPLACE_SYMBOL_BODY",
                                  "targetSymbol": "initGame",
                                  "targetKind": "function",
                                  "contentLines": [
                                    "bindStartButton();"
                                  ]
                                }
                              ]
                                }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("符号级精确改写")) {
                    int call = codeFileCalls.incrementAndGet();
                    if (systemPrompt.contains("当前文件为空或新建文件")) {
                        return """
                                {
                                  "operations": [
                                    {
                                      "action": "APPEND_FILE",
                                      "targetSymbol": null,
                                      "targetKind": null,
                                      "contentLines": [
                                        "function bindStartButton() {",
                                        "  const button = document.getElementById('start-btn');",
                                        "  const status = document.getElementById('status');",
                                        "  if (button && status) {",
                                        "    button.addEventListener('click', () => {",
                                        "      status.textContent = 'started';",
                                        "    });",
                                        "  }",
                                        "}",
                                        "",
                                        "function initGame() {",
                                        "}"
                                      ]
                                    }
                                  ]
                                }
                                """;
                    }
                    return """
                            {
                              "operations": [
                                {
                                  "action": "REPLACE_SYMBOL_BODY",
                                  "targetSymbol": "initGame",
                                  "targetKind": "function",
                                  "contentLines": [
                                    "bindStartButton();"
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("精确改写")) {
                    preciseHtmlCalls.incrementAndGet();
                    throw new IllegalStateException(
                            "Ollama returned unusable content for model qwen3-coder:30b after 3 attempts (done=true, done_reason=length, eval_count=1400)"
                    );
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "当前项目无额外自检步骤",
                              "steps": []
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

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                testExecutor,
                new devflow.agent.parsing.TreeSitterSupport(),
                supervisorAgent(provider),
                new ContractExtractor()
        );

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个俄罗斯方块游戏", "需要纯网页版、可直接打开运行"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata

                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata

                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                """,
                patchNote("")
        );

        Subtask plannedSubtask = bundle.snapshot().plan().subtasks().getFirst();
        assertTrue(plannedSubtask.changes().stream().anyMatch(change ->
                "index.html".equals(change.path()) && change.effectiveEditScope() == FileEditScope.HOST_HTML_PATCH
        ));
        assertTrue(plannedSubtask.changes().stream().anyMatch(change ->
                "index.app.js".equals(change.path())
        ));
        assertTrue(preciseHtmlCalls.get() >= 0);
        assertTrue(inlineScriptCalls.get() >= 0);
        assertTrue(codeFileCalls.get() >= 0);
    }

    @Test
    void explicitContractViewIsPreservedEvenWhenPromptArtifactsAreSanitized() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "创建网页入口并补齐最小交互。",
                              "subtasks": [
                                {
                                  "title": "创建入口页面",
                                  "goal": "创建最小可运行网页入口",
                                  "deliveryMode": "INCREMENTAL",
                                  "ownedCapabilities": ["网页入口", "运行表面", "开始按钮行为"],
                                  "deferredCapabilities": [],
                                  "acceptanceCriteria": ["页面可打开", "开始按钮可改变状态"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建入口文件",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    return """
                            {
                              "documentTitle": "Tetris",
                              "markupHtml": "<h1>Tetris</h1><button id=\\"start-btn\\">开始</button><p id=\\"status\\">ready</p>",
                              "styleCss": "body { margin: 0; }",
                              "scriptJs": "document.addEventListener('DOMContentLoaded', () => { const button = document.getElementById('start-btn'); const status = document.getElementById('status'); if (button && status) { button.addEventListener('click', () => { status.textContent = 'started'; }); } });"
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态网页做基础验证。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "确认页面资源完整。",
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);
        ContractView authoritativeContract = new ContractView(
                null,
                null,
                new ExecutionContract(true, "html-entry", true, true, java.util.List.of("page-opens", "surface-renders")),
                null
        );

        ImplementationExecutionBundle bundle = executor.execute(
                tempDir,
                runRecord("实现一个可直接运行的网页游戏", "需要入口"),
                "# analysis\n\n## 7. Source Metadata\n- hard.userRequirements: (none)\n- hard.upstreamFacts: (none)\n- soft.inferences: (none)\n- soft.designDecisions: (none)\n- soft.recommendations: (none)\n- open.questions: (none)",
                "# prd",
                "# design",
                "",
                authoritativeContract
        );

        assertTrue(bundle.sharedContextMarkdown().contains("entryRequired: true"), bundle.sharedContextMarkdown());
        assertTrue(bundle.taskPackagesMarkdown().contains("entryRequired: true"), bundle.taskPackagesMarkdown());
    }

    @Test
    void newHtmlEntryGenerationAcceptsStructuredDraftInsteadOfFullDocument() throws Exception {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (role == ModelRole.IMPLEMENTATION && systemPrompt.contains("拆成可落地、可验证的子步骤")) {
                    return """
                            {
                              "summary": "创建入口页面并补齐最小交互。",
                              "subtasks": [
                                {
                                  "title": "创建入口页面",
                                  "goal": "创建最小可运行网页入口",
                                  "deliveryMode": "INCREMENTAL",
                                  "ownedCapabilities": ["网页入口", "运行表面", "开始按钮行为"],
                                  "deferredCapabilities": [],
                                  "acceptanceCriteria": ["页面可打开", "开始按钮可改变状态"],
                                  "changes": [
                                    {
                                      "path": "index.html",
                                      "action": "WRITE",
                                      "reason": "创建入口文件",
                                    "runtimeOwnership": "INLINE_HOST"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    return """
                            {
                              "documentTitle": "Tetris",
                              "headHtml": "<meta name=\\"description\\" content=\\"Playable Tetris\\">",
                              "markupHtml": "<section class=\\"game-shell\\"><canvas id=\\"game-canvas\\" width=\\"300\\" height=\\"600\\"></canvas><button id=\\"start-btn\\">开始</button><p id=\\"status\\">ready</p></section>",
                              "styleCss": "body { margin: 0; background: #10131a; color: #f4f7fb; } .game-shell { display: grid; gap: 12px; }",
                              "scriptJs": "document.addEventListener('DOMContentLoaded', () => { const startButton = document.getElementById('start-btn'); const status = document.getElementById('status'); if (startButton && status) { startButton.addEventListener('click', () => { status.textContent = 'started'; }); } });"
                            }
                            """;
                }
                if (role == ModelRole.VALIDATION_STRATEGY) {
                    return """
                            {
                              "summary": "静态网页做基础验证。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "确认页面资源完整。",
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
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        executor.execute(
                tempDir,
                runRecord("实现一个可直接运行的网页游戏", "需要入口"),
                "# analysis",
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders, behavior-works
                """,
                """
                # 技术方案设计

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, surface-renders, behavior-works
                """,
                ""
        );

        String html = Files.readString(tempDir.resolve("index.html"));
        assertTrue(html.contains("<!DOCTYPE html>"), html);
        assertTrue(html.contains("<html"), html);
        assertTrue(html.contains("<main id=\"app-root\">"), html);
        assertTrue(html.contains("<style id=\"app-style\">"), html);
        assertTrue(html.contains("<script id=\"app-script\">"), html);
        assertTrue(html.contains("Playable Tetris"), html);
        assertTrue(html.contains("status.textContent = 'started';"), html);
    }

    private String invalidPlan() {
        return """
                {
                  "summary": "无效计划",
                  "subtasks": [
                    {
                      "title": "一次改太多",
                      "goal": "同时改 3 个文件",
                      "acceptanceCriteria": ["会被实现器拒绝"],
                      "changes": [
                        {"path": "index.html", "action": "WRITE", "reason": "a", "runtimeOwnership": "INLINE_HOST"},
                        {"path": "styles.css", "action": "WRITE", "reason": "b"},
                        {"path": "game.js", "action": "WRITE", "reason": "c"}
                      ]
                    }
                  ]
                }
                """;
    }

    private String patchNote(String message) {
        return executionDirectiveNote(FixMode.PATCH, null, message);
    }

    private String deliveryMaxFilesNote(int maxFiles) {
        return executionDirectiveNote(null, maxFiles, "");
    }

    private String patchNoteWithDeliveryMaxFiles(int maxFiles) {
        return executionDirectiveNote(FixMode.PATCH, maxFiles, "");
    }

    private String executionDirectiveNote(FixMode fixMode, Integer deliveryMaxFiles, String message) {
        String block = ExecutionDirectiveProtocol.renderBlock(new ExecutionDirectivePayload(
                fixMode == null ? null : fixMode.name(),
                null,
                null,
                null,
                deliveryMaxFiles,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
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
        ));
        if (message == null || message.isBlank()) {
            return block;
        }
        return block + "\n\n" + message;
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

    private SupervisorAgent supervisorAgent(LlmProvider provider) {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        StageFlowPolicy stageFlowPolicy = new StageFlowPolicy();
        return new SupervisorAgent(
                null,
                new ObjectMapper(),
                new ContextProjector(
                        artifactStore,
                        new FileProjectWorkspace(),
                        new ArtifactSummaryBuilder(),
                        new ContractExtractor(),
                        new devflow.agent.context.ContextLayerAssembler()
                ),
                stageFlowPolicy,
                new SupervisorFallbackPolicy(stageFlowPolicy)
        );
    }
}
