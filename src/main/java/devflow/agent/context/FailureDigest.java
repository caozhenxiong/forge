package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.domain.StageType;

public record FailureDigest(
        StageType stageType,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems
) {

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                """.formatted(
                language.choose("阶段", "stage"),
                stageType,
                language.choose("摘要", "summary"),
                blank(summary, language),
                language.choose("修改要求", "changeRequest"),
                blank(changeRequest, language),
                language.choose("证据", "evidence"),
                blank(evidence, language),
                language.choose("行动项", "actionItems"),
                blank(actionItems, language)
        ).trim();
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
