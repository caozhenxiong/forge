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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void frontendTaskDefaultsFirstSubtaskToSkeletonMode() {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> capturedFileSystemPrompt = new AtomicReference<>("");
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
                                      "reason": "创建网页入口"
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                if (role == ModelRole.IMPLEMENTATION) {
                    capturedFileSystemPrompt.set(systemPrompt);
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
                return review(systemPrompt, candidateContent, options, null);
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor testExecutor = new TestExecutor(workspace, provider, objectMapper);
        ImplementationExecutor executor = new ImplementationExecutor(provider, workspace, objectMapper, testExecutor);

        String report = executor.execute(
                tempDir,
                runRecord("实现一个网页版俄罗斯方块", "需要纯网页版、像素风"),
                "# analysis",
                "# prd",
                "# design",
                ""
        );

        assertTrue(report.contains("交付模式：SKELETON"));
        assertTrue(capturedFileSystemPrompt.get().contains("当前处于骨架模式"));
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

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(
                        tempDir,
                        runRecord("实现一个网页应用", ""),
                        "# analysis",
                        "# prd",
                        "# design",
                        ""
                )
        );

        assertTrue(exception.getMessage().contains("Failed to parse implementation plan"));
    }

    @Test
    void rejectsMalformedHtmlGeneratedDuringImplementation() {
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
                                      "reason": "创建页面入口"
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

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(
                        tempDir,
                        runRecord("实现一个网页版应用", "需要纯网页版"),
                        "# analysis",
                        "# prd",
                        "# design",
                        ""
                )
        );

        assertTrue(exception.getMessage().contains("incomplete or invalid"));
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
                                      "reason": "在稳定锚点内精确更新内容"
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

        String report = executor.execute(
                tempDir,
                runRecord("实现一个俄罗斯方块页面", "需要纯网页版"),
                "# analysis",
                "# prd",
                "# design",
                "[FIX_MODE=PATCH]"
        );

        String html = Files.readString(tempDir.resolve("index.html"));
        assertTrue(report.contains("交付模式：PATCH"));
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

        String report = executor.execute(
                tempDir,
                runRecord("修复 Java 方法", ""),
                "# analysis",
                "# prd",
                "# design",
                "[FIX_MODE=PATCH]"
        );

        String javaSource = Files.readString(tempDir.resolve("App.java"));
        assertTrue(report.contains("交付模式：PATCH"));
        assertTrue(javaSource.contains("System.out.println(\"patched\");"));
        assertTrue(javaSource.contains("class App"));
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
                        {"path": "index.html", "action": "WRITE", "reason": "a"},
                        {"path": "styles.css", "action": "WRITE", "reason": "b"},
                        {"path": "game.js", "action": "WRITE", "reason": "c"}
                      ]
                    }
                  ]
                }
                """;
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
}
