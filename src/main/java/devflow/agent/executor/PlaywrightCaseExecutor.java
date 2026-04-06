package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.CommandResult;
import devflow.agent.project.FileProjectWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class PlaywrightCaseExecutor {

    private final FileProjectWorkspace workspace;
    private final ObjectMapper objectMapper;

    public PlaywrightCaseExecutor(FileProjectWorkspace workspace, ObjectMapper objectMapper) {
        this.workspace = workspace;
        this.objectMapper = objectMapper;
    }

    public List<TestCaseResult> execute(Path projectPath, TestCasePlan plan) {
        try {
            Path tempFile = Files.createTempFile("devflow-testcases-", ".json");
            Files.writeString(tempFile, objectMapper.writeValueAsString(plan));
            CommandResult result = workspace.runCommand(
                    projectPath,
                    List.of(
                            "node",
                            Path.of("/home/linus/workspace/devflow-agent/tools/playwright-smoke/run-testcases.mjs").toString(),
                            tempFile.toString(),
                            projectPath.toString()
                    ),
                    Duration.ofSeconds(90)
            );
            Files.deleteIfExists(tempFile);
            if (result.exitCode() != 0 && (result.stdout() == null || result.stdout().isBlank())) {
                return List.of(new TestCaseResult("EXECUTOR", "Playwright 用例执行器", false, true, trim(result.stderr())));
            }
            ExecutionPayload payload = objectMapper.readValue(nonBlank(result.stdout(), result.stderr()), ExecutionPayload.class);
            return payload.cases() == null
                    ? List.of(new TestCaseResult("EXECUTOR", "Playwright 用例执行器", false, true, "未返回测试结果。"))
                    : payload.cases().stream()
                    .map(caseResult -> new TestCaseResult(
                            caseResult.id(),
                            caseResult.title(),
                            caseResult.passed(),
                            caseResult.required() == null || caseResult.required(),
                            trim(caseResult.details())
                    ))
                    .toList();
        } catch (Exception exception) {
            return List.of(new TestCaseResult("EXECUTOR", "Playwright 用例执行器", false, true, exception.getMessage()));
        }
    }

    private String nonBlank(String stdout, String stderr) {
        return stdout != null && !stdout.isBlank() ? stdout : stderr;
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        return value.length() > 3000 ? value.substring(0, 3000) + "\n...<truncated>" : value;
    }

    private record ExecutionPayload(
            @JsonProperty("cases") List<ExecutionCasePayload> cases
    ) {
    }

    private record ExecutionCasePayload(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("required") Boolean required,
            @JsonProperty("passed") boolean passed,
            @JsonProperty("details") String details
    ) {
    }
}
