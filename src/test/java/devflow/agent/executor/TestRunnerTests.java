package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRunnerTests {

    @TempDir
    Path tempDir;

    @Test
    void returnsBlockedCasesWhenSelectionIsUnavailable() {
        TestRunner runner = new TestRunner(new PlaywrightCaseExecutor(new FileProjectWorkspace(), new ObjectMapper()));

        TestRunReport report = runner.executeDetailed(
                tempDir,
                new TestCasePlan("summary", List.of(new TestCaseSpec(
                        "TC-001",
                        "不可执行用例",
                        "smoke",
                        true,
                        "",
                        "",
                        "",
                        List.of()
                ))),
                new TestToolSelection(
                        TestExecutionTool.UNAVAILABLE,
                        TestToolFailureReason.UNSUPPORTED_EXECUTOR,
                        "当前技术栈尚未实现专用 testcase 执行器，测试用例未执行。",
                        "当前技术栈没有匹配的 testcase 执行器。",
                        null
                )
        );

        List<TestCaseResult> results = report.caseResults();
        assertEquals(1, results.size());
        assertEquals(TestCaseStatus.BLOCKED, results.getFirst().status());
        assertEquals("unsupported-executor", results.getFirst().failureReason());
        assertEquals(1, report.toolResults().size());
        assertEquals(ToolStatus.SKIPPED, report.toolResults().getFirst().status());
        assertEquals(ToolName.TEST_CASE_EXECUTION, report.toolResults().getFirst().toolName());
    }

    @Test
    void missingCasesAreReportedAsStructuredToolFailure() {
        TestRunner runner = new TestRunner(new PlaywrightCaseExecutor(new FileProjectWorkspace(), new ObjectMapper()));

        TestRunReport report = runner.executeDetailed(
                tempDir,
                new TestCasePlan("summary", List.of()),
                new TestToolSelection(
                        TestExecutionTool.UNAVAILABLE,
                        TestToolFailureReason.UNSUPPORTED_EXECUTOR,
                        "不可执行",
                        "无执行器",
                        null
                )
        );

        assertEquals(1, report.caseResults().size());
        assertEquals(ToolFailureCode.TEST_CASES_MISSING, report.toolResults().getFirst().failureCode());
        assertTrue(report.toolResults().getFirst().evidence().contains("未生成任何可执行 testcase"));
    }

    @Test
    void runtimeSnapshotReturnsNullWhenSelectionDoesNotSupportIt() {
        TestRunner runner = new TestRunner(new PlaywrightCaseExecutor(new FileProjectWorkspace(), new ObjectMapper()));

        RuntimeSnapshotCaptureResult captureResult = runner.captureRuntimeSnapshotDetailed(
                tempDir,
                new TestToolSelection(
                        TestExecutionTool.UNAVAILABLE,
                        TestToolFailureReason.UNSUPPORTED_EXECUTOR,
                        "不可执行",
                        "无执行器",
                        null
                )
        );

        assertNull(captureResult.runtimeSnapshot());
        assertEquals(ToolStatus.SKIPPED, captureResult.toolResult().status());
        assertEquals(ToolName.RUNTIME_SNAPSHOT_CAPTURE, captureResult.toolResult().toolName());
    }
}
