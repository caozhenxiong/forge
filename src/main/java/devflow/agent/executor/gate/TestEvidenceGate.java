package devflow.agent.executor.gate;

import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.testing.TestCaseResult;
import devflow.agent.executor.testing.TestCaseStatus;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.CoverageLedger;
import java.util.ArrayList;
import java.util.List;

/**
 * 把测试阶段的确定性证据汇总收成统一 gate。
 *
 * <p>它不决定是否回退到哪一个阶段，也不解释复杂语义，只做三件事：
 * 1. 汇总必测用例是否真的执行并通过；
 * 2. 汇总 self-check / architect check 是否通过；
 * 3. 给出稳定的 summary、计数和 gate disposition。
 */
public class TestEvidenceGate implements DeterministicGate<TestEvidenceGateInput> {

    public TestEvidenceGateOutcome evaluateDetailed(TestEvidenceGateInput input, DocumentLanguage language) {
        List<TestCaseResult> caseResults = input == null || input.caseResults() == null ? List.of() : input.caseResults();
        SelfCheckResult selfCheck = input == null ? null : input.selfCheck();
        ArchitectIntegrationCheckResult architectCheck = input == null ? null : input.architectCheck();
        CoverageLedger coverageLedger = input == null ? null : input.coverageLedger();

        long totalCases = caseResults.size();
        long passedCases = caseResults.stream().filter(TestCaseResult::passed).count();
        long requiredFailedCases = caseResults.stream().filter(result -> result.required() && result.status() == TestCaseStatus.FAILED).count();
        long requiredBlockedCases = caseResults.stream().filter(result -> result.required() && result.status() == TestCaseStatus.BLOCKED).count();
        boolean requiredCasesPassed = caseResults.stream()
                .filter(TestCaseResult::required)
                .allMatch(result -> result.status() == TestCaseStatus.PASSED);
        boolean finalPassed = selfCheck != null
                && selfCheck.passed()
                && architectCheck != null
                && architectCheck.passed()
                && requiredCasesPassed
                && (coverageLedger == null || !coverageLedger.hasMissingRequiredCoverage());

        List<GateIssue> issues = new ArrayList<>();
        if (caseResults.isEmpty()) {
            issues.add(new GateIssue(
                    "TEST_CASES_MISSING",
                    language.choose("测试阶段未生成任何可执行 testcase。", "The test stage did not produce any executable test cases."),
                    GateFailureDisposition.REPLAN_CURRENT_STAGE
            ));
        }
        if (selfCheck != null && !selfCheck.passed()) {
            issues.add(new GateIssue(
                    "SELF_CHECK_FAILED",
                    language.choose("技术自检未通过。", "Technical self-check failed."),
                    GateFailureDisposition.ESCALATE
            ));
        }
        if (architectCheck != null && !architectCheck.passed()) {
            issues.add(new GateIssue(
                    "ARCHITECT_CHECK_FAILED",
                    language.choose("当前交付物未通过整体可运行检查。", "The current deliverable did not pass the overall runnable check."),
                    GateFailureDisposition.ESCALATE
            ));
        }
        if (requiredBlockedCases > 0) {
            issues.add(new GateIssue(
                    "REQUIRED_TESTS_BLOCKED",
                    language.choose("存在未执行或被阻塞的必测用例。", "There are required test cases that were blocked or not executed."),
                    GateFailureDisposition.ESCALATE
            ));
        }
        if (requiredFailedCases > 0) {
            issues.add(new GateIssue(
                    "REQUIRED_TESTS_FAILED",
                    language.choose("存在失败的必测用例。", "There are failed required test cases."),
                    GateFailureDisposition.ESCALATE
            ));
        }
        if (coverageLedger != null && coverageLedger.hasMissingRequiredCoverage()) {
            issues.add(new GateIssue(
                    "REQUIRED_CAPABILITY_COVERAGE_MISSING",
                    language.choose("存在未被覆盖或未通过的必需能力项。", "Required capability coverage is missing or not passing."),
                    GateFailureDisposition.ESCALATE
            ));
        }

        String summary = finalPassed
                ? language.choose("自检通过，且必测用例全部通过。", "Self-check passed and all required test cases passed.")
                : architectCheck != null && !architectCheck.passed()
                ? language.choose("当前交付物未通过整体可运行检查。", "The current deliverable did not pass the overall runnable check.")
                : requiredBlockedCases > 0
                ? language.choose("存在未执行或被阻塞的必测用例。", "There are required test cases that were blocked or not executed.")
                : requiredFailedCases > 0
                ? language.choose("存在失败的必测用例。", "There are failed required test cases.")
                : coverageLedger != null && coverageLedger.hasMissingRequiredCoverage()
                ? language.choose("存在未被覆盖或未通过的必需能力项。", "Required capability coverage is missing or not passing.")
                : language.choose("技术自检未通过。", "Technical self-check failed.");

        GateReport report = issues.isEmpty()
                ? GateReport.success()
                : GateReport.failure(summary, issues);
        return new TestEvidenceGateOutcome(
                report,
                finalPassed,
                totalCases,
                passedCases,
                requiredFailedCases,
                requiredBlockedCases,
                summary
        );
    }

    @Override
    public GateReport evaluate(TestEvidenceGateInput input) {
        return evaluateDetailed(input, DocumentLanguage.ZH).report();
    }
}
