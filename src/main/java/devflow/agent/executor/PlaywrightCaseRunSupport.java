package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.CommandResult;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 负责执行 Playwright 用例计划并解析返回结果。
 */
final class PlaywrightCaseRunSupport {

    private final FileProjectWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final PlaywrightSupport support;

    PlaywrightCaseRunSupport(FileProjectWorkspace workspace, ObjectMapper objectMapper, PlaywrightSupport support) {
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.support = support;
    }

    List<TestCaseResult> execute(Path projectPath, TestCasePlan plan) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("devflow-testcases-", ".json");
            Files.writeString(tempFile, objectMapper.writeValueAsString(plan));
            CommandResult result = workspace.runCommand(
                    projectPath,
                    List.of(
                            "node",
                            PlaywrightSupport.TESTCASE_SCRIPT_PATH.toString(),
                            tempFile.toString(),
                            projectPath.toString()
                    ),
                    PlaywrightExecutionPolicy.caseExecutionTimeout()
            );
            if (result.exitCode() != 0 && (result.stdout() == null || result.stdout().isBlank())) {
                return List.of(blockedExecutorResult(support.trim(result.stderr()), "executor-failure"));
            }
            ExecutionEnvelope payload = objectMapper.readValue(support.nonBlank(result.stdout(), result.stderr()), ExecutionEnvelope.class);
            if (payload.cases() == null) {
                return List.of(blockedExecutorResult(missingResultsMessage(payload), "missing-results"));
            }
            return payload.cases().stream()
                    .map(caseResult -> new TestCaseResult(
                            caseResult.id(),
                            caseResult.title(),
                            caseResult.status() == null ? (caseResult.passed() ? TestCaseStatus.PASSED : TestCaseStatus.FAILED) : caseResult.status(),
                            caseResult.required() == null || caseResult.required(),
                            support.trim(caseResult.details()),
                            support.trim(caseResult.failureReason()),
                            support.trim(caseResult.evidence())
                    ))
                    .toList();
        } catch (Exception exception) {
            return List.of(blockedExecutorResult(exception.getMessage(), "executor-exception"));
        } finally {
            support.deleteQuietly(tempFile);
        }
    }

    private TestCaseResult blockedExecutorResult(String details, String failureReason) {
        return new TestCaseResult(
                "EXECUTOR",
                "Playwright 用例执行器",
                TestCaseStatus.BLOCKED,
                true,
                details,
                failureReason,
                details
        );
    }

    private String missingResultsMessage(ExecutionEnvelope payload) {
        if (payload == null) {
            return "未返回测试结果。";
        }
        StringBuilder builder = new StringBuilder("未返回测试结果。");
        if (payload.status() != null && !payload.status().isBlank()) {
            builder.append(" status=").append(payload.status());
        }
        if (payload.errors() != null && !payload.errors().isEmpty()) {
            builder.append(" errors=").append(String.join(" | ", payload.errors()));
        }
        return builder.toString();
    }

    private record ExecutionEnvelope(
            @JsonProperty("status") String status,
            @JsonProperty("errors") List<String> errors,
            @JsonProperty("cases") List<ExecutionCasePayload> cases
    ) {
    }

    private record ExecutionCasePayload(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("required") Boolean required,
            @JsonProperty("passed") boolean passed,
            @JsonProperty("status") TestCaseStatus status,
            @JsonProperty("details") String details,
            @JsonProperty("failureReason") String failureReason,
            @JsonProperty("evidence") String evidence
    ) {
    }
}
