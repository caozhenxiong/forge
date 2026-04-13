package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import java.nio.file.Path;
import java.util.List;

/**
 * 测试阶段的确定性执行器。
 *
 * <p>它负责把“工具选择结果”变成真正的执行行为：
 * 1. 如果存在匹配执行器，就运行 testcase；
 * 2. 如果当前不可执行，就稳定地产出 blocked/failed 结果；
 * 3. 如果允许抓取 runtime snapshot，就走对应执行器。
 *
 * <p>它不负责：
 * 1. 规划 testcase；
 * 2. 汇总测试证据 gate；
 * 3. 渲染测试报告。
 */
class TestRunner {

    private static final String EXECUTOR_CASE_ID = "EXECUTOR";
    private static final String OBSERVATION_CONTRACT_INVALID_REASON = "observation-contract-invalid";

    private final PlaywrightCaseExecutor playwrightCaseExecutor;

    TestRunner(PlaywrightCaseExecutor playwrightCaseExecutor) {
        this.playwrightCaseExecutor = playwrightCaseExecutor;
    }

    List<TestCaseResult> execute(Path projectPath, TestCasePlan plan, TestToolSelection toolSelection) {
        return executeDetailed(projectPath, plan, toolSelection).caseResults();
    }

    TestRunReport executeDetailed(Path projectPath, TestCasePlan plan, TestToolSelection toolSelection) {
        if (plan.cases() == null || plan.cases().isEmpty()) {
            return new TestRunReport(
                    List.of(new TestCaseResult(
                            "NO-CASES",
                            "未生成测试用例",
                            TestCaseStatus.BLOCKED,
                            true,
                            "测试阶段未生成任何可执行用例。",
                            "missing-testcases",
                            "未生成任何可执行 testcase。"
                    )),
                    List.of(ToolResult.failure(
                            ToolName.TEST_CASE_EXECUTION,
                            ToolFailureCode.TEST_CASES_MISSING,
                            "测试阶段未生成任何可执行 testcase。",
                            null
                    ))
            );
        }
        if (toolSelection.executable() && toolSelection.tool() == TestExecutionTool.PLAYWRIGHT) {
            List<TestCaseResult> caseResults = playwrightCaseExecutor.execute(projectPath, plan);
            return new TestRunReport(
                    caseResults,
                    List.of(deriveExecutionToolResult(caseResults))
            );
        }
        List<TestCaseResult> caseResults = plan.cases().stream()
                .map(testCase -> new TestCaseResult(
                        testCase.id(),
                        testCase.title(),
                        testCase.required() ? TestCaseStatus.BLOCKED : TestCaseStatus.FAILED,
                        testCase.required(),
                        toolSelection.details(),
                        toolSelection.failureReason() == null ? "" : toolSelection.failureReason().wireValue(),
                        toolSelection.evidence()
                ))
                .toList();
        return new TestRunReport(
                caseResults,
                List.of(ToolResult.skipped(
                        ToolName.TEST_CASE_EXECUTION,
                        toolSelection.evidence(),
                        null
                ))
        );
    }

    TestRunReport blockedByValidation(TestCasePlan plan, UiRuntimeContractValidation validation) {
        String details = validation != null && validation.kind() == UiRuntimeContractValidationKind.PROBE_INVALID
                ? "浏览器 runtime probe 无效，测试用例未执行。"
                : "运行时观测契约无效，测试用例未执行。";
        String evidence = validation == null || validation.evidence().isBlank()
                ? "UI runtime contract is invalid."
                : validation.evidence();
        List<TestCaseSpec> plannedCases = plan == null || plan.cases() == null ? List.of() : plan.cases();
        List<TestCaseResult> caseResults = plannedCases.stream()
                .map(testCase -> new TestCaseResult(
                        testCase.id(),
                        testCase.title(),
                        testCase.required() ? TestCaseStatus.BLOCKED : TestCaseStatus.FAILED,
                        testCase.required(),
                        details,
                        OBSERVATION_CONTRACT_INVALID_REASON,
                        evidence
                ))
                .toList();
        return new TestRunReport(
                caseResults,
                List.of(ToolResult.skipped(
                        ToolName.TEST_CASE_EXECUTION,
                        details + " " + evidence,
                        null
                ))
        );
    }

    RuntimeSnapshot captureRuntimeSnapshot(Path projectPath, TestToolSelection toolSelection) {
        return captureRuntimeSnapshotDetailed(projectPath, toolSelection).runtimeSnapshot();
    }

    RuntimeSnapshotCaptureResult captureRuntimeSnapshotDetailed(Path projectPath, TestToolSelection toolSelection) {
        if (toolSelection == null || !toolSelection.supportsRuntimeSnapshot()) {
            return new RuntimeSnapshotCaptureResult(
                    null,
                    ToolResult.skipped(
                            ToolName.RUNTIME_SNAPSHOT_CAPTURE,
                            toolSelection == null ? "当前测试阶段未选择运行时快照工具。" : toolSelection.evidence(),
                            null
                    )
            );
        }
        RuntimeSnapshot runtimeSnapshot = playwrightCaseExecutor.captureRuntimeSnapshot(projectPath, toolSelection.runtimeSnapshotEntry());
        if (runtimeSnapshot == null || !runtimeSnapshot.probeCaptured()) {
            String evidence = runtimeSnapshot == null
                    ? "运行时快照执行器未返回任何结果。"
                    : runtimeSnapshot.captureErrors() == null || runtimeSnapshot.captureErrors().isEmpty()
                    ? "运行时快照采集失败。"
                    : String.join(" | ", runtimeSnapshot.captureErrors());
            return new RuntimeSnapshotCaptureResult(
                    runtimeSnapshot,
                    ToolResult.failure(
                            ToolName.RUNTIME_SNAPSHOT_CAPTURE,
                            ToolFailureCode.RUNTIME_SNAPSHOT_CAPTURE_FAILED,
                            evidence,
                            null
                    )
            );
        }
        return new RuntimeSnapshotCaptureResult(
                runtimeSnapshot,
                ToolResult.success(ToolName.RUNTIME_SNAPSHOT_CAPTURE)
        );
    }

    /**
     * 把 testcase 结果回收成工具级结论。
     *
     * <p>这里不把业务用例失败等同于“工具失败”。
     * 只有执行器自己没有产出有效结果，或者直接在执行器层面阻塞时，
     * 才把它记成 `TEST_CASE_EXECUTION_FAILED`。
     */
    private ToolResult deriveExecutionToolResult(List<TestCaseResult> caseResults) {
        if (caseResults == null || caseResults.isEmpty()) {
            return ToolResult.failure(
                    ToolName.TEST_CASE_EXECUTION,
                    ToolFailureCode.TEST_CASE_EXECUTION_FAILED,
                    "测试执行器未返回任何 testcase 结果。",
                    null
            );
        }
        TestCaseResult executorFailure = caseResults.stream()
                .filter(result -> EXECUTOR_CASE_ID.equals(result.id()))
                .filter(TestCaseResult::blocked)
                .findFirst()
                .orElse(null);
        if (executorFailure != null) {
            return ToolResult.failure(
                    ToolName.TEST_CASE_EXECUTION,
                    ToolFailureCode.TEST_CASE_EXECUTION_FAILED,
                    blank(executorFailure.evidence()).isBlank() ? executorFailure.details() : executorFailure.evidence(),
                    null
            );
        }
        return ToolResult.success(ToolName.TEST_CASE_EXECUTION);
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
