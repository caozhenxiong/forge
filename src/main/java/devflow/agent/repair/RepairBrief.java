package devflow.agent.repair;

import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
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
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                # %s

                - failureCluster: %s
                - recommendedMode: %s

                ## %s
                %s

                ## %s
                %s

                ## %s
                %s

                ## %s
                %s

                ## %s
                %s

                ## %s
                %s

                ## %s
                %s

                ## %s
                %s

                ## %s
                %s
                """.formatted(
                language.choose("修复摘要", "Repair Brief"),
                nullToPlaceholder(failureCluster, language),
                recommendedMode == null ? FixMode.PATCH : recommendedMode,
                language.choose("重复错误", "Repeated Errors"),
                renderList(repeatedErrors, language),
                language.choose("根因假设", "Root Cause Hypothesis"),
                nullToPlaceholder(rootCauseHypothesis, language),
                language.choose("受影响文件", "Affected Files"),
                renderList(affectedFiles, language),
                language.choose("证据", "Evidence"),
                renderList(evidence, language),
                ArtifactLabels.mustFixFirst(language),
                renderList(mustFixFirst, language),
                ArtifactLabels.forbiddenDirections(language),
                renderList(forbiddenDirections, language),
                language.choose("不要改动", "Do Not Change"),
                renderList(doNotChange, language),
                language.choose("验收目标", "Acceptance Target"),
                renderList(acceptanceTarget, language),
                language.choose("验收检查", "Acceptance Checks"),
                renderList(acceptanceChecks, language)
        );
    }

    private String renderList(List<String> items, DocumentLanguage language) {
        if (items == null || items.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        return items.stream()
                .map(item -> "- " + item)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(PlaceholderValues.bulletNone(language));
    }

    private String nullToPlaceholder(String value, DocumentLanguage language) {
        return PlaceholderValues.orUnknown(value, language);
    }
}
