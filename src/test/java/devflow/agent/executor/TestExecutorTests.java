package devflow.agent.executor;

import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.validation.ProjectInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void staticWebCheckAcceptsThisPropertyAndInlineClickHandler() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="restartButton">重新开始</button>
                          <script>
                            class GameController {
                              constructor() {
                                this.restartButton = document.getElementById('restartButton');
                                this.bindEvents();
                              }

                              bindEvents() {
                                this.restartButton.addEventListener('click', () => {
                                  this.restart();
                                });
                              }

                              restart() {}
                            }
                          </script>
                        </body>
                        </html>
                        """
        );

        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("验证策略规划器")) {
                    return """
                            {
                              "summary": "对静态网页项目执行资源与脚本语法检查。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "先确认本地资源引用完整。",
                                  "required": true
                                },
                                {
                                  "capability": "WEB_JAVASCRIPT_SYNTAX_CHECK",
                                  "reason": "再确认脚本语法可执行。",
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
        TestExecutor executor = new TestExecutor(new FileProjectWorkspace(), provider, new com.fasterxml.jackson.databind.ObjectMapper());
        SelfCheckResult result = executor.selfCheck(tempDir);

        assertTrue(result.passed(), result.details());
    }

    @Test
    void projectInspectorTreatsSingleHtmlWithInlineScriptAsWebStatic() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <script>
                            console.log('inline');
                          </script>
                        </body>
                        </html>
                        """
        );

        ProjectInspector inspector = new ProjectInspector(new FileProjectWorkspace());
        assertEquals("web-static", inspector.inspect(tempDir).projectType());
        assertEquals("index.html", inspector.inspect(tempDir).resolvedHtmlEntryPath());
    }

    @Test
    void projectInspectorResolvesHtmlEntryWhenIndexIsMissing() throws Exception {
        Files.writeString(
                tempDir.resolve("play.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <main id="app"></main>
                        </body>
                        </html>
                        """
        );

        ProjectInspector inspector = new ProjectInspector(new FileProjectWorkspace());
        assertEquals("play.html", inspector.inspect(tempDir).resolvedHtmlEntryPath());
    }

    @Test
    void unsupportedTestcaseExecutorNoLongerMarksRequiredCasesAsPassed() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# no runnable stack");

        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("验证策略规划器")) {
                    return """
                            {
                              "summary": "无可用自检步骤",
                              "steps": []
                            }
                            """;
                }
                if (systemPrompt.contains("测试用例设计器")) {
                    return """
                            {
                              "summary": "生成一条 required 用例",
                              "cases": [
                                {
                                  "id": "TC-001",
                                  "title": "未实现执行器的用例",
                                  "type": "smoke",
                                  "required": true,
                                  "entry": "",
                                  "preconditions": "",
                                  "expected": "应被标记为未执行",
                                  "steps": [
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
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor executor = new TestExecutor(new FileProjectWorkspace(), provider, new com.fasterxml.jackson.databind.ObjectMapper());
        TestExecutionBundle bundle = executor.execute(tempDir, "goal", "", "", "", "", "");

        assertTrue(bundle.executionMarkdown().contains("未执行"));
        assertTrue(bundle.reportMarkdown().contains("decision: REJECTED"));
    }
}
