package devflow.agent.executor.runtime;

import devflow.agent.executor.*;

import java.nio.file.Path;
import java.util.List;

public record WebRuntimeWiringResult(
        boolean passed,
        List<String> issues,
        List<String> evidence,
        HtmlEntryRuntimeOwnershipInspection ownershipInspection,
        RuntimeWiringPatchDecision patchDecision
) {

    public static WebRuntimeWiringResult success() {
        return new WebRuntimeWiringResult(true, List.of(), List.of(), null, null);
    }

    public static WebRuntimeWiringResult success(Path htmlEntryPath, HtmlEntryRuntimeOwnershipInspection ownershipInspection) {
        RuntimeWiringPatchDecision patchDecision = ownershipInspection == null
                ? null
                : new RuntimeWiringPatchDecisionResolver().resolve(htmlEntryPath, ownershipInspection, List.of());
        return new WebRuntimeWiringResult(true, List.of(), List.of(), ownershipInspection, patchDecision);
    }

    public static WebRuntimeWiringResult failure(
            Path htmlEntryPath,
            List<String> issues,
            List<String> evidence,
            HtmlEntryRuntimeOwnershipInspection ownershipInspection
    ) {
        RuntimeWiringPatchDecision patchDecision = new RuntimeWiringPatchDecisionResolver().resolve(
                htmlEntryPath,
                ownershipInspection,
                issues
        );
        return new WebRuntimeWiringResult(false, List.copyOf(issues), List.copyOf(evidence), ownershipInspection, patchDecision);
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
