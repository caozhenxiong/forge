package devflow.agent.executor;

import java.util.List;

public record WebRuntimeWiringResult(
        boolean passed,
        List<String> issues,
        List<String> evidence
) {

    public static WebRuntimeWiringResult success() {
        return new WebRuntimeWiringResult(true, List.of(), List.of());
    }

    public static WebRuntimeWiringResult failure(List<String> issues, List<String> evidence) {
        return new WebRuntimeWiringResult(false, List.copyOf(issues), List.copyOf(evidence));
    }

    public String summary() {
        return String.join(" ", issues == null ? List.of() : issues);
    }

    public String evidenceMarkdown() {
        if (evidence == null || evidence.isEmpty()) {
            return "";
        }
        return evidence.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> "- " + item.trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
