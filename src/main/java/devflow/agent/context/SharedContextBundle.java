package devflow.agent.context;

import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

public record SharedContextBundle(
        String goal,
        String constraints,
        ContractView contractView,
        List<String> requiredEvidence,
        List<String> mustFixFirst,
        List<String> forbiddenDirections,
        String repairSummary,
        String workingSetSummary
) {

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                # %s

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
                language.choose("共享上下文包", "Shared Context Bundle"),
                language.choose("目标", "Goal"),
                blank(goal, language),
                language.choose("约束", "Constraints"),
                blank(constraints, language),
                language.choose("结构化契约", "Structured Contracts"),
                contractView == null ? empty(language) : contractView.toMarkdown(language),
                ArtifactLabels.requiredEvidence(language),
                bullets(requiredEvidence, language),
                ArtifactLabels.mustFixFirst(language),
                bullets(mustFixFirst, language),
                ArtifactLabels.forbiddenDirections(language),
                bullets(forbiddenDirections, language),
                language.choose("修复摘要", "Repair Summary"),
                blank(repairSummary, language),
                language.choose("工作集摘要", "Working Set Summary"),
                blank(workingSetSummary, language)
        ).trim();
    }

    private String bullets(List<String> values, DocumentLanguage language) {
        if (values == null || values.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .map(value -> "- " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(PlaceholderValues.bulletNone(language));
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }

    private String empty(DocumentLanguage language) {
        return PlaceholderValues.none(language);
    }
}
