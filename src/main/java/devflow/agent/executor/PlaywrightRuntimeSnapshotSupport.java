package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.CommandResult;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 负责抓取 Playwright 运行时快照。
 */
final class PlaywrightRuntimeSnapshotSupport {

    private final FileProjectWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final PlaywrightSupport support;

    PlaywrightRuntimeSnapshotSupport(FileProjectWorkspace workspace, ObjectMapper objectMapper, PlaywrightSupport support) {
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.support = support;
    }

    RuntimeSnapshot capture(Path projectPath, String entry) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("devflow-runtime-snapshot-", ".json");
            Files.writeString(tempFile, "{\"entry\":\"" + support.escapeJson(entry) + "\"}");
            CommandResult result = workspace.runCommand(
                    projectPath,
                    List.of(
                            "node",
                            PlaywrightSupport.TESTCASE_SCRIPT_PATH.toString(),
                            "--snapshot",
                            tempFile.toString(),
                            projectPath.toString()
                    ),
                    PlaywrightExecutionPolicy.snapshotTimeout()
            );
            if (result.exitCode() != 0 && (result.stdout() == null || result.stdout().isBlank())) {
                return failedSnapshot(entry, support.trim(result.stderr()));
            }
            SnapshotPayload payload = objectMapper.readValue(support.nonBlank(result.stdout(), result.stderr()), SnapshotPayload.class);
            return new RuntimeSnapshot(
                    entry,
                    payload.pageTitle(),
                    payload.pageLoadMs(),
                    payload.canvasCount() == null ? 0 : payload.canvasCount(),
                    payload.selectors() == null ? List.of() : normalizeSelectors(payload.selectors()),
                    payload.exposedMetricKeys() == null ? List.of() : List.copyOf(payload.exposedMetricKeys()),
                    payload.consoleErrors() == null ? List.of() : payload.consoleErrors(),
                    payload.pageErrors() == null ? List.of() : payload.pageErrors()
            );
        } catch (Exception exception) {
            return failedSnapshot(entry, exception.getMessage());
        } finally {
            support.deleteQuietly(tempFile);
        }
    }

    private RuntimeSnapshot failedSnapshot(String entry, String error) {
        return new RuntimeSnapshot(entry, "", null, 0, List.of(), List.of(), List.of(error), List.of());
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

    private record SnapshotPayload(
            @JsonProperty("pageTitle") String pageTitle,
            @JsonProperty("pageLoadMs") Integer pageLoadMs,
            @JsonProperty("canvasCount") Integer canvasCount,
            @JsonProperty("selectors") List<String> selectors,
            @JsonProperty("exposedMetricKeys") List<String> exposedMetricKeys,
            @JsonProperty("consoleErrors") List<String> consoleErrors,
            @JsonProperty("pageErrors") List<String> pageErrors
    ) {
    }
}
