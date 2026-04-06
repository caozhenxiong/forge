package devflow.agent.executor;

import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "# 产品需求文档\n\n- 游戏加载时间小于2秒\n",
                "# 技术方案\n\n- 页面加载时间小于 2000ms\n",
                ""
        );

        TestCaseSpec performanceCase = plan.cases().stream()
                .filter(testCase -> "TC-PERF-LOAD".equals(testCase.id()))
                .findFirst()
                .orElseThrow();

        assertEquals("performance", performanceCase.type());
        assertTrue(performanceCase.required());
        assertEquals("MEASURE_PAGE_LOAD_MAX_MS", performanceCase.steps().getFirst().action());
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
                ""
        );

        assertTrue(plan.cases().stream().anyMatch(testCase ->
                testCase.steps().stream().anyMatch(step -> "#startBtn".equals(step.selector()))
        ));
        assertTrue(plan.cases().stream().anyMatch(testCase ->
                testCase.steps().stream().anyMatch(step -> ".pause-btn".equals(step.selector()))
        ));
    }
}
