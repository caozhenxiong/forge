package devflow.agent.quality;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

/**
 * 当前轮质量清单。
 *
 * <p>这层把质量要求收成显式 checklist，供 planning / implementation verification / review / test 共享，
 * 避免每个阶段各自猜当前到底该看什么。
 */
public record QualityChecklist(
        List<String> structureChecks,
        List<String> coverageChecks,
        List<String> experienceChecks
) {

    public QualityChecklist {
        structureChecks = structureChecks == null ? List.of() : List.copyOf(structureChecks);
        coverageChecks = coverageChecks == null ? List.of() : List.copyOf(coverageChecks);
        experienceChecks = experienceChecks == null ? List.of() : List.copyOf(experienceChecks);
    }

    public static QualityChecklist empty() {
        return new QualityChecklist(List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return structureChecks.isEmpty() && coverageChecks.isEmpty() && experienceChecks.isEmpty();
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ### %s
                %s

                ### %s
                %s

                ### %s
                %s
                """.formatted(
                language.choose("结构清单", "Structure Checklist"),
                bullets(structureChecks, language),
                language.choose("覆盖清单", "Coverage Checklist"),
                bullets(coverageChecks, language),
                language.choose("体验清单", "Experience Checklist"),
                bullets(experienceChecks, language)
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
}
