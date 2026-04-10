package devflow.agent.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

public record ConstraintSourceMetadata(
        List<String> hardUserRequirements,
        List<String> hardUpstreamFacts,
        List<String> softInferences,
        List<String> softDesignDecisions,
        List<String> softRecommendations,
        List<String> openQuestions
) {

    public static ConstraintSourceMetadata empty() {
        return new ConstraintSourceMetadata(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(int sectionNumber, DocumentLanguage language) {
        String heading = ArtifactLabels.sourceMetadata(language);
        return """
                ## %d. %s

                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                """.formatted(
                sectionNumber,
                heading,
                SourceMetadataKeys.HARD_USER_REQUIREMENTS,
                renderList(hardUserRequirements),
                SourceMetadataKeys.HARD_UPSTREAM_FACTS,
                renderList(hardUpstreamFacts),
                SourceMetadataKeys.SOFT_INFERENCES,
                renderList(softInferences),
                SourceMetadataKeys.SOFT_DESIGN_DECISIONS,
                renderList(softDesignDecisions),
                SourceMetadataKeys.SOFT_RECOMMENDATIONS,
                renderList(softRecommendations),
                SourceMetadataKeys.OPEN_QUESTIONS,
                renderList(openQuestions)
        ).trim();
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ## %s

                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                """.formatted(
                ArtifactLabels.sourceMetadata(language),
                SourceMetadataKeys.HARD_USER_REQUIREMENTS,
                renderList(hardUserRequirements),
                SourceMetadataKeys.HARD_UPSTREAM_FACTS,
                renderList(hardUpstreamFacts),
                SourceMetadataKeys.SOFT_INFERENCES,
                renderList(softInferences),
                SourceMetadataKeys.SOFT_DESIGN_DECISIONS,
                renderList(softDesignDecisions),
                SourceMetadataKeys.SOFT_RECOMMENDATIONS,
                renderList(softRecommendations),
                SourceMetadataKeys.OPEN_QUESTIONS,
                renderList(openQuestions)
        ).trim();
    }

    public ConstraintSourceMetadata merge(ConstraintSourceMetadata other) {
        if (other == null) {
            return this;
        }
        return new ConstraintSourceMetadata(
                mergeLists(hardUserRequirements, other.hardUserRequirements),
                mergeLists(hardUpstreamFacts, other.hardUpstreamFacts),
                mergeLists(softInferences, other.softInferences),
                mergeLists(softDesignDecisions, other.softDesignDecisions),
                mergeLists(softRecommendations, other.softRecommendations),
                mergeLists(openQuestions, other.openQuestions)
        );
    }

    /**
     * 这是派生辅助信息，不属于结构化协议字段。
     * 如果让 Jackson 把它当成属性写回 JSON，会污染 SOURCE_METADATA block。
     */
    @JsonIgnore
    public boolean isEmpty() {
        return isEmptyList(hardUserRequirements)
                && isEmptyList(hardUpstreamFacts)
                && isEmptyList(softInferences)
                && isEmptyList(softDesignDecisions)
                && isEmptyList(softRecommendations)
                && isEmptyList(openQuestions);
    }

    private static List<String> mergeLists(List<String> left, List<String> right) {
        return java.util.stream.Stream.concat(
                        left == null ? java.util.stream.Stream.empty() : left.stream(),
                        right == null ? java.util.stream.Stream.empty() : right.stream()
                )
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String renderList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return PlaceholderValues.machineNone();
        }
        String rendered = items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse(PlaceholderValues.machineNone());
        return rendered.isBlank() ? PlaceholderValues.machineNone() : rendered;
    }

    private boolean isEmptyList(List<String> items) {
        return items == null || items.stream().noneMatch(item -> item != null && !item.isBlank());
    }
}
