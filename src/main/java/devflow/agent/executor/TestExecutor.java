package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.validation.ValidationExecutor;
import devflow.agent.validation.ValidationPlan;
import devflow.agent.validation.ValidationStrategyPlanner;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TestExecutor {

    private final ProjectInspector projectInspector;
    private final ValidationStrategyPlanner strategyPlanner;
    private final ValidationExecutor validationExecutor;
    private final TestCasePlanner testCasePlanner;
    private final PlaywrightCaseExecutor playwrightCaseExecutor;
    private final Map<Path, ValidationPlan> cachedPlans = new ConcurrentHashMap<>();

    public TestExecutor(FileProjectWorkspace workspace, LlmProvider llmProvider, ObjectMapper objectMapper) {
        this.projectInspector = new ProjectInspector(workspace);
        this.strategyPlanner = new ValidationStrategyPlanner(llmProvider, objectMapper);
        this.validationExecutor = new ValidationExecutor(workspace);
        this.testCasePlanner = new TestCasePlanner(workspace, llmProvider, objectMapper);
        this.playwrightCaseExecutor = new PlaywrightCaseExecutor(workspace, objectMapper);
    }

    public TestExecutionBundle execute(
            Path projectPath,
            String goal,
            String constraints,
            String prd,
            String design,
            String implementationReport,
            String note
    ) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        ProjectFingerprint fingerprint = projectInspector.inspect(normalized);
        SelfCheckResult selfCheck = selfCheck(normalized, fingerprint);
        TestCasePlan testCasePlan = testCasePlanner.plan(normalized, fingerprint, goal, constraints, prd, design, implementationReport);
        List<TestCaseResult> caseResults = executeCases(normalized, fingerprint, testCasePlan);
        boolean requiredCasesPassed = caseResults.stream().filter(TestCaseResult::required).allMatch(TestCaseResult::passed);
        boolean finalPassed = selfCheck.passed() && requiredCasesPassed;

        String testCasesMarkdown = renderTestCases(testCasePlan);
        String executionMarkdown = renderExecution(selfCheck, caseResults);
        String reportMarkdown = renderReport(note, selfCheck, caseResults, finalPassed);
        return new TestExecutionBundle(testCasesMarkdown, executionMarkdown, reportMarkdown);
    }

    public SelfCheckResult selfCheck(Path projectPath) {
        Path normalized = projectPath.toAbsolutePath().normalize();
        ProjectFingerprint fingerprint = projectInspector.inspect(normalized);
        return selfCheck(normalized, fingerprint);
    }

    private SelfCheckResult selfCheck(Path projectPath, ProjectFingerprint fingerprint) {
        ValidationPlan plan = cachedPlans.computeIfAbsent(projectPath, ignored -> strategyPlanner.plan(fingerprint));
        return validationExecutor.execute(projectPath, fingerprint, plan);
    }

    private List<TestCaseResult> executeCases(Path projectPath, ProjectFingerprint fingerprint, TestCasePlan plan) {
        if (plan.cases() == null || plan.cases().isEmpty()) {
            return List.of(new TestCaseResult("NO-CASES", "未生成测试用例", false, true, "测试阶段未生成任何可执行用例。"));
        }
        if ("web-static".equals(fingerprint.projectType()) || "web-app".equals(fingerprint.projectType())) {
            return playwrightCaseExecutor.execute(projectPath, plan);
        }
        return plan.cases().stream()
                .map(testCase -> new TestCaseResult(
                        testCase.id(),
                        testCase.title(),
                        false,
                        testCase.required(),
                        "当前技术栈尚未实现专用 testcase 执行器，测试用例未执行。"
                ))
                .toList();
    }

    private String renderTestCases(TestCasePlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 测试用例设计\n\n");
        builder.append("- summary: ").append(blank(plan.summary())).append("\n\n");
        int index = 1;
        for (TestCaseSpec testCase : plan.cases()) {
            builder.append("## Case ").append(index++).append(": ").append(blank(testCase.title())).append("\n\n");
            builder.append("- id: ").append(blank(testCase.id())).append("\n");
            builder.append("- type: ").append(blank(testCase.type())).append("\n");
            builder.append("- required: ").append(testCase.required()).append("\n");
            builder.append("- entry: ").append(blank(testCase.entry())).append("\n");
            builder.append("- preconditions: ").append(blank(testCase.preconditions())).append("\n");
            builder.append("- expected: ").append(blank(testCase.expected())).append("\n\n");
            builder.append("### Steps\n\n");
            int stepIndex = 1;
            for (TestStepSpec step : testCase.steps()) {
                builder.append(stepIndex++)
                        .append(". ")
                        .append(step.action());
                if (!blank(step.selector()).isBlank()) {
                    builder.append(" selector=").append(step.selector());
                }
                if (!blank(step.key()).isBlank()) {
                    builder.append(" key=").append(step.key());
                }
                if (step.count() != null) {
                    builder.append(" count=").append(step.count());
                }
                if (step.ms() != null) {
                    builder.append(" ms=").append(step.ms());
                }
                if (!blank(step.text()).isBlank()) {
                    builder.append(" text=").append(step.text());
                }
                if (Boolean.TRUE.equals(step.optional())) {
                    builder.append(" optional=true");
                }
                builder.append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String renderExecution(SelfCheckResult selfCheck, List<TestCaseResult> caseResults) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 测试执行记录\n\n");
        builder.append("## Self-check\n\n");
        builder.append("- passed: ").append(selfCheck.passed()).append("\n");
        builder.append("- summary: ").append(blank(selfCheck.summary())).append("\n\n");
        builder.append("```text\n").append(trim(selfCheck.details())).append("\n```\n\n");
        builder.append("## Test Case Results\n\n");
        for (TestCaseResult result : caseResults) {
            builder.append("- ")
                    .append(result.id())
                    .append(" | ")
                    .append(result.title())
                    .append(" | required=")
                    .append(result.required())
                    .append(" | passed=")
                    .append(result.passed())
                    .append("\n");
            builder.append("  detail: ").append(trim(result.details()).replace("\n", " | ")).append("\n");
        }
        builder.append("\n");
        return builder.toString();
    }

    private String renderReport(String note, SelfCheckResult selfCheck, List<TestCaseResult> caseResults, boolean finalPassed) {
        long totalCases = caseResults.size();
        long passedCases = caseResults.stream().filter(TestCaseResult::passed).count();
        long requiredFailedCases = caseResults.stream().filter(result -> result.required() && !result.passed()).count();
        String summary = finalPassed
                ? "自检通过，且必测用例全部通过。"
                : requiredFailedCases > 0
                ? "存在未通过的必测用例。"
                : "技术自检未通过。";
        return """
                # 测试报告

                - decision: %s
                - note: %s
                - summary: %s
                - selfCheckPassed: %s
                - totalCases: %d
                - passedCases: %d
                - requiredFailedCases: %d

                ## 结论

                %s
                """.formatted(
                finalPassed ? "APPROVED" : "REJECTED",
                note,
                summary,
                selfCheck.passed(),
                totalCases,
                passedCases,
                requiredFailedCases,
                requiredFailedCases == 0
                        ? "所有必测 test case 已通过。"
                        : "仍有必测 test case 未通过，不能视为测试完成。"
        );
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        return value.length() > 12000 ? value.substring(0, 12000) + "\n...<truncated>" : value;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
