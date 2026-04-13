package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.shell.CommandResult;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 网页 probe 的唯一 Java 入口。
 *
 * <p>smoke validation 与 runtime snapshot 都必须经过这层，
 * 避免同一份浏览器事实被两套脚本或两套 payload 解析逻辑分裂消费。
 */
public final class PlaywrightProbeRunner {

    private final FileProjectWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final PlaywrightSupport support;

    public PlaywrightProbeRunner(FileProjectWorkspace workspace, ObjectMapper objectMapper) {
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.support = new PlaywrightSupport();
    }

    public ProbeOutcome probe(Path projectPath, String entry) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("devflow-runtime-probe-", ".json");
            Files.writeString(tempFile, "{\"entry\":\"" + support.escapeJson(entry) + "\"}");
            CommandResult result = workspace.runCommand(
                    projectPath,
                    List.of(
                            "node",
                            PlaywrightSupport.TESTCASE_SCRIPT_PATH.toString(),
                            "--probe",
                            tempFile.toString(),
                            projectPath.toString()
                    ),
                    PlaywrightExecutionPolicy.snapshotTimeout()
            );
            String rawPayload = support.nonBlank(result.stdout(), result.stderr());
            if (rawPayload == null || rawPayload.isBlank()) {
                RuntimeSnapshot snapshot = collectorFailedSnapshot(entry, "Playwright probe returned no payload.");
                return new ProbeOutcome(snapshot, "");
            }
            ProbeEnvelope envelope = objectMapper.readValue(rawPayload, ProbeEnvelope.class);
            if (!"ok".equals(blank(envelope.status()))) {
                String error = joinErrors(envelope.errors());
                RuntimeSnapshot snapshot = collectorFailedSnapshot(
                        entry,
                        error.isBlank() ? "Playwright probe failed." : error
                );
                return new ProbeOutcome(snapshot, support.trim(rawPayload));
            }
            if (envelope.probe() == null) {
                RuntimeSnapshot snapshot = payloadInvalidSnapshot(entry, "Playwright probe payload is missing probe.");
                return new ProbeOutcome(snapshot, support.trim(rawPayload));
            }
            return new ProbeOutcome(
                    capturedSnapshot(entry, envelope.probe()),
                    support.trim(rawPayload)
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            RuntimeSnapshot snapshot = payloadInvalidSnapshot(entry, exception.getOriginalMessage());
            return new ProbeOutcome(snapshot, support.trim(exception.getMessage()));
        } catch (Exception exception) {
            RuntimeSnapshot snapshot = collectorFailedSnapshot(entry, exception.getMessage());
            return new ProbeOutcome(snapshot, support.trim(exception.getMessage()));
        } finally {
            support.deleteQuietly(tempFile);
        }
    }

    private RuntimeSnapshot capturedSnapshot(String entry, ProbePayload payload) {
        return new RuntimeSnapshot(
                entry,
                payload.pageTitle(),
                payload.pageLoadMs(),
                payload.canvasCount() == null ? 0 : payload.canvasCount(),
                payload.selectors() == null ? List.of() : normalizeSelectors(payload.selectors()),
                payload.surfaceCandidates() == null ? List.of() : payload.surfaceCandidates().stream()
                        .map(candidate -> new RuntimeSurfaceCandidate(
                                candidate.selector(),
                                devflow.agent.util.EnumParsers.parseIgnoreCase(
                                        UiObservationMode.class,
                                        candidate.mode(),
                                        UiObservationMode.DOM_SIGNATURE
                                ),
                                candidate.area() == null ? 0L : candidate.area()
                        ))
                        .filter(RuntimeSurfaceCandidate::usable)
                        .toList(),
                payload.controlCandidates() == null ? List.of() : payload.controlCandidates().stream()
                        .map(candidate -> new RuntimeControlCandidate(candidate.selector(), candidate.text()))
                        .filter(RuntimeControlCandidate::usable)
                        .toList(),
                payload.exposedMetricKeys() == null ? List.of() : List.copyOf(payload.exposedMetricKeys()),
                payload.consoleErrors() == null ? List.of() : payload.consoleErrors(),
                payload.pageErrors() == null ? List.of() : payload.pageErrors(),
                RuntimeSnapshotCaptureStatus.CAPTURED,
                RuntimeSnapshotFailureCode.NONE,
                List.of()
        );
    }

    private RuntimeSnapshot collectorFailedSnapshot(String entry, String error) {
        return new RuntimeSnapshot(
                entry,
                "",
                null,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                RuntimeSnapshotCaptureStatus.COLLECTOR_FAILED,
                RuntimeSnapshotFailureCode.COLLECTOR_EXECUTION_FAILED,
                blank(error).isBlank() ? List.of("Playwright probe failed.") : List.of(blank(error))
        );
    }

    private RuntimeSnapshot payloadInvalidSnapshot(String entry, String error) {
        return new RuntimeSnapshot(
                entry,
                "",
                null,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                RuntimeSnapshotCaptureStatus.PAYLOAD_INVALID,
                RuntimeSnapshotFailureCode.PAYLOAD_INVALID,
                blank(error).isBlank() ? List.of("Playwright probe payload is invalid.") : List.of(blank(error))
        );
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

    private String joinErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    public record ProbeOutcome(
            RuntimeSnapshot runtimeSnapshot,
            String evidence
    ) {
        public boolean success() {
            return runtimeSnapshot != null && runtimeSnapshot.probeCaptured();
        }
    }

    private record ProbeEnvelope(
            @JsonProperty("status") String status,
            @JsonProperty("probe") ProbePayload probe,
            @JsonProperty("errors") List<String> errors
    ) {
    }

    private record ProbePayload(
            @JsonProperty("pageTitle") String pageTitle,
            @JsonProperty("pageLoadMs") Integer pageLoadMs,
            @JsonProperty("canvasCount") Integer canvasCount,
            @JsonProperty("selectors") List<String> selectors,
            @JsonProperty("surfaceCandidates") List<SurfaceCandidatePayload> surfaceCandidates,
            @JsonProperty("controlCandidates") List<ControlCandidatePayload> controlCandidates,
            @JsonProperty("exposedMetricKeys") List<String> exposedMetricKeys,
            @JsonProperty("consoleErrors") List<String> consoleErrors,
            @JsonProperty("pageErrors") List<String> pageErrors
    ) {
    }

    private record SurfaceCandidatePayload(
            @JsonProperty("selector") String selector,
            @JsonProperty("mode") String mode,
            @JsonProperty("area") Long area
    ) {
    }

    private record ControlCandidatePayload(
            @JsonProperty("selector") String selector,
            @JsonProperty("text") String text
    ) {
    }
}
