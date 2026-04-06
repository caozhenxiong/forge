package devflow.agent.repair;

import devflow.agent.review.FixMode;
import java.util.List;

public record RepairBrief(
        String failureCluster,
        List<String> repeatedErrors,
        String rootCauseHypothesis,
        List<String> affectedFiles,
        List<String> evidence,
        FixMode recommendedMode,
        List<String> mustFixFirst,
        List<String> forbiddenDirections,
        List<String> doNotChange,
        List<String> acceptanceTarget,
        List<String> acceptanceChecks
) {

    public String toMarkdown() {
        return """
                # Repair Brief

                - failureCluster: %s
                - recommendedMode: %s

                ## Repeated Errors
                %s

                ## Root Cause Hypothesis
                %s

                ## Affected Files
                %s

                ## Evidence
                %s

                ## Must Fix First
                %s

                ## Forbidden Directions
                %s

                ## Do Not Change
                %s

                ## Acceptance Target
                %s

                ## Acceptance Checks
                %s
                """.formatted(
                nullToPlaceholder(failureCluster),
                recommendedMode == null ? FixMode.PATCH : recommendedMode,
                renderList(repeatedErrors),
                nullToPlaceholder(rootCauseHypothesis),
                renderList(affectedFiles),
                renderList(evidence),
                renderList(mustFixFirst),
                renderList(forbiddenDirections),
                renderList(doNotChange),
                renderList(acceptanceTarget),
                renderList(acceptanceChecks)
        );
    }

    private String renderList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- (none)";
        }
        return items.stream()
                .map(item -> "- " + item)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- (none)");
    }

    private String nullToPlaceholder(String value) {
        return value == null || value.isBlank() ? "(unknown)" : value;
    }
}
