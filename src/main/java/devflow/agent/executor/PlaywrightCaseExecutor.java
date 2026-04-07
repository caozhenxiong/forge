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
import java.util.Set;
import java.util.TreeSet;

public class PlaywrightCaseExecutor {

    private final FileProjectWorkspace workspace;
    private final ObjectMapper objectMapper;

    public PlaywrightCaseExecutor(FileProjectWorkspace workspace, ObjectMapper objectMapper) {
        this.workspace = workspace;
        this.objectMapper = objectMapper;
    }

    public List<TestCaseResult> execute(Path projectPath, TestCasePlan plan) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("devflow-testcases-", ".json");
            Files.writeString(tempFile, objectMapper.writeValueAsString(plan));
            CommandResult result = workspace.runCommand(
                    projectPath,
                    List.of(
                            "node",
                            Path.of("tools", "playwright-smoke", "run-testcases.mjs").toString(),
                            tempFile.toString(),
                            projectPath.toString()
                    ),
                    Duration.ofSeconds(90)
            );
            if (result.exitCode() != 0 && (result.stdout() == null || result.stdout().isBlank())) {
                return List.of(new TestCaseResult(
                        "EXECUTOR",
                        "Playwright 用例执行器",
                        TestCaseStatus.BLOCKED,
                        true,
                        trim(result.stderr()),
                        "executor-failure",
                        trim(result.stderr())
                ));
            }
            ExecutionPayload payload = objectMapper.readValue(nonBlank(result.stdout(), result.stderr()), ExecutionPayload.class);
            return payload.cases() == null
                    ? List.of(new TestCaseResult(
                    "EXECUTOR",
                    "Playwright 用例执行器",
                    TestCaseStatus.BLOCKED,
                    true,
                    "未返回测试结果。",
                    "missing-results",
                    "Playwright 未返回 cases。"
            ))
                    : payload.cases().stream()
                    .map(caseResult -> new TestCaseResult(
                            caseResult.id(),
                            caseResult.title(),
                            caseResult.status() == null ? (caseResult.passed() ? TestCaseStatus.PASSED : TestCaseStatus.FAILED) : caseResult.status(),
                            caseResult.required() == null || caseResult.required(),
                            trim(caseResult.details()),
                            trim(caseResult.failureReason()),
                            trim(caseResult.evidence())
                    ))
                    .toList();
        } catch (Exception exception) {
            return List.of(new TestCaseResult(
                    "EXECUTOR",
                    "Playwright 用例执行器",
                    TestCaseStatus.BLOCKED,
                    true,
                    exception.getMessage(),
                    "executor-exception",
                    exception.getMessage()
            ));
        } finally {
            deleteQuietly(tempFile);
        }
    }

    public RuntimeSnapshot captureRuntimeSnapshot(Path projectPath, String entry) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("devflow-runtime-snapshot-", ".json");
            Files.writeString(tempFile, "{\"entry\":\"" + escapeJson(entry) + "\"}");
            CommandResult result = workspace.runCommand(
                    projectPath,
                    List.of(
                            "node",
                            Path.of("tools", "playwright-smoke", "run-testcases.mjs").toString(),
                            "--snapshot",
                            tempFile.toString(),
                            projectPath.toString()
                    ),
                    Duration.ofSeconds(60)
            );
            if (result.exitCode() != 0 && (result.stdout() == null || result.stdout().isBlank())) {
                return new RuntimeSnapshot(entry, "", null, 0, List.of(), List.of(trim(result.stderr())), List.of());
            }
            SnapshotPayload payload = objectMapper.readValue(nonBlank(result.stdout(), result.stderr()), SnapshotPayload.class);
            return new RuntimeSnapshot(
                    entry,
                    payload.pageTitle(),
                    payload.pageLoadMs(),
                    payload.canvasCount() == null ? 0 : payload.canvasCount(),
                    payload.selectors() == null ? List.of() : normalizeSelectors(payload.selectors()),
                    payload.consoleErrors() == null ? List.of() : payload.consoleErrors(),
                    payload.pageErrors() == null ? List.of() : payload.pageErrors()
            );
        } catch (Exception exception) {
            return new RuntimeSnapshot(entry, "", null, 0, List.of(), List.of(exception.getMessage()), List.of());
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private String nonBlank(String stdout, String stderr) {
        return stdout != null && !stdout.isBlank() ? stdout : stderr;
    }

    private List<String> normalizeSelectors(List<String> selectors) {
        Set<String> normalized = new TreeSet<>();
        for (String selector : selectors) {
            if (selector == null || selector.isBlank()) {
                continue;
            }
            normalized.add(selector.trim());
        }
        return List.copyOf(normalized);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
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
            @JsonProperty("status") TestCaseStatus status,
            @JsonProperty("details") String details,
            @JsonProperty("failureReason") String failureReason,
            @JsonProperty("evidence") String evidence
    ) {
    }

    private record SnapshotPayload(
            @JsonProperty("pageTitle") String pageTitle,
            @JsonProperty("pageLoadMs") Integer pageLoadMs,
            @JsonProperty("canvasCount") Integer canvasCount,
            @JsonProperty("selectors") List<String> selectors,
            @JsonProperty("consoleErrors") List<String> consoleErrors,
            @JsonProperty("pageErrors") List<String> pageErrors
    ) {
    }
}
