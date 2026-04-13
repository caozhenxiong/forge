package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.LinkedHashSet;
import java.util.List;

public record RuntimeSnapshot(
        String entry,
        String pageTitle,
        Integer pageLoadMs,
        int canvasCount,
        List<String> selectors,
        List<RuntimeSurfaceCandidate> surfaceCandidates,
        List<RuntimeControlCandidate> controlCandidates,
        List<String> exposedMetricKeys,
        List<String> consoleErrors,
        List<String> pageErrors,
        RuntimeSnapshotCaptureStatus captureStatus,
        RuntimeSnapshotFailureCode captureFailureCode,
        List<String> captureErrors
) {

    public RuntimeSnapshot(
            String entry,
            String pageTitle,
            Integer pageLoadMs,
            int canvasCount,
            List<String> selectors,
            List<RuntimeSurfaceCandidate> surfaceCandidates,
            List<RuntimeControlCandidate> controlCandidates,
            List<String> exposedMetricKeys,
            List<String> consoleErrors,
            List<String> pageErrors
    ) {
        this(
                entry,
                pageTitle,
                pageLoadMs,
                canvasCount,
                selectors,
                surfaceCandidates,
                controlCandidates,
                exposedMetricKeys,
                consoleErrors,
                pageErrors,
                RuntimeSnapshotCaptureStatus.CAPTURED,
                RuntimeSnapshotFailureCode.NONE,
                List.of()
        );
    }

    public RuntimeSnapshot(
            String entry,
            String pageTitle,
            Integer pageLoadMs,
            int canvasCount,
            List<String> selectors,
            List<String> exposedMetricKeys,
            List<String> consoleErrors,
            List<String> pageErrors
    ) {
        this(
                entry,
                pageTitle,
                pageLoadMs,
                canvasCount,
                selectors,
                List.of(),
                List.of(),
                exposedMetricKeys,
                consoleErrors,
                pageErrors,
                RuntimeSnapshotCaptureStatus.CAPTURED,
                RuntimeSnapshotFailureCode.NONE,
                List.of()
        );
    }

    public RuntimeSnapshot {
        selectors = selectors == null ? List.of() : List.copyOf(selectors);
        surfaceCandidates = surfaceCandidates == null ? List.of() : List.copyOf(surfaceCandidates);
        controlCandidates = controlCandidates == null ? List.of() : List.copyOf(controlCandidates);
        exposedMetricKeys = exposedMetricKeys == null ? List.of() : List.copyOf(exposedMetricKeys);
        consoleErrors = consoleErrors == null ? List.of() : List.copyOf(consoleErrors);
        pageErrors = pageErrors == null ? List.of() : List.copyOf(pageErrors);
        captureStatus = captureStatus == null ? RuntimeSnapshotCaptureStatus.CAPTURED : captureStatus;
        captureFailureCode = captureFailureCode == null ? RuntimeSnapshotFailureCode.NONE : captureFailureCode;
        captureErrors = captureErrors == null ? List.of() : List.copyOf(captureErrors);
    }

    public boolean usable() {
        return selectors != null && !selectors.isEmpty();
    }

    public boolean probeCaptured() {
        return captureStatus == RuntimeSnapshotCaptureStatus.CAPTURED;
    }

    public boolean probeInvalid() {
        return captureStatus == RuntimeSnapshotCaptureStatus.COLLECTOR_FAILED
                || captureStatus == RuntimeSnapshotCaptureStatus.PAYLOAD_INVALID;
    }

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                # %s

                - entry: %s
                - %s: %s
                - pageLoadMs: %s
                - canvasCount: %s
                - surfaceCandidates: %s
                - controlCandidates: %s
                - exposedMetricKeys: %s
                - captureStatus: %s
                - captureFailureCode: %s
                - captureErrors: %s
                - consoleErrors: %s
                - pageErrors: %s

                ## %s

                %s
                """.formatted(
                language.choose("运行时快照", "Runtime Snapshot"),
                blank(entry, language),
                language.choose("页面标题", "pageTitle"),
                blank(pageTitle, language),
                pageLoadMs == null ? "n/a" : pageLoadMs,
                canvasCount,
                surfaceCandidates == null ? 0 : surfaceCandidates.size(),
                controlCandidates == null ? 0 : controlCandidates.size(),
                exposedMetricKeys == null ? 0 : exposedMetricKeys.size(),
                captureStatus,
                captureFailureCode,
                captureErrors == null ? 0 : captureErrors.size(),
                consoleErrors == null ? 0 : consoleErrors.size(),
                pageErrors == null ? 0 : pageErrors.size(),
                language.choose("选择器", "Selectors"),
                renderList(selectors, language)
        ).trim();
    }

    public boolean exposesMetric(String metricKey) {
        if (metricKey == null || metricKey.isBlank() || exposedMetricKeys == null || exposedMetricKeys.isEmpty()) {
            return false;
        }
        return exposedMetricKeys.stream().anyMatch(metricKey::equals);
    }

    public boolean hasControlSelector(String selector) {
        if (selector == null || selector.isBlank() || controlCandidates == null || controlCandidates.isEmpty()) {
            return false;
        }
        return controlCandidates.stream().anyMatch(candidate ->
                candidate != null
                        && candidate.usable()
                        && selector.trim().equals(candidate.selector())
        );
    }

    public List<String> controlSelectors() {
        if (controlCandidates == null || controlCandidates.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        for (RuntimeControlCandidate candidate : controlCandidates) {
            if (candidate == null || !candidate.usable()) {
                continue;
            }
            selectors.add(candidate.selector());
        }
        return List.copyOf(selectors);
    }

    private String renderList(List<String> values, DocumentLanguage language) {
        if (values == null || values.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(value.trim());
        }
        return builder.isEmpty() ? PlaceholderValues.bulletNone(language) : builder.toString();
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
