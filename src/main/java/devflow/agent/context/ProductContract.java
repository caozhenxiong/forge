package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record ProductContract(
        List<String> objectives,
        List<String> userScenarios,
        List<String> requiredCapabilities,
        List<String> optionalCapabilities,
        List<String> nonFunctionalRequirements,
        List<String> acceptanceCriteria,
        List<String> nonGoals,
        List<RequirementReference> requirementReferences
) {

    public ProductContract {
        objectives = freezeTextList(objectives);
        userScenarios = freezeTextList(userScenarios);
        requiredCapabilities = freezeTextList(requiredCapabilities);
        optionalCapabilities = freezeTextList(optionalCapabilities);
        nonFunctionalRequirements = freezeTextList(nonFunctionalRequirements);
        acceptanceCriteria = freezeTextList(acceptanceCriteria);
        nonGoals = freezeTextList(nonGoals);
        requirementReferences = normalizeRequirementReferences(requirementReferences);
    }

    /**
     * PRD 六段式正文在进入 machine block 前，需要被确定性投影成显式 coverage refs。
     *
     * <p>这里只允许固定章节结构决定 obligation：
     * 1. 3.1 核心功能 -> planning required；
     * 2. 3.2 可选增强 -> optional；
     * 3. 5.x 验收标准 -> final acceptance。
     *
     * <p>这样 implementation gate 不再需要从 prose 猜“这条是不是可选/待确认”。
     */
    public static ProductContract projectedFromPrdSections(
            List<String> objectives,
            List<String> userScenarios,
            List<String> requiredCapabilities,
            List<String> optionalCapabilities,
            List<String> nonFunctionalRequirements,
            List<String> acceptanceCriteria,
            List<String> nonGoals
    ) {
        return new ProductContract(
                objectives,
                userScenarios,
                requiredCapabilities,
                optionalCapabilities,
                nonFunctionalRequirements,
                acceptanceCriteria,
                nonGoals,
                deriveRequirementReferences(requiredCapabilities, optionalCapabilities, acceptanceCriteria)
        );
    }

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ## %s

                > %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s
                """.formatted(
                language.choose("产品契约（参考）", "Product Contract (Reference Only)"),
                language.choose("以下内容用于帮助后续节点理解产品目标与验收语义，但不单独构成绑定硬约束；绑定约束以 Execution Contract 与 Source Metadata 中的 hard.* 为准。", "The following content helps downstream nodes understand product intent and acceptance semantics, but does not by itself create binding hard constraints; binding constraints come from the Execution Contract and Source Metadata hard.* entries."),
                language.choose("目标", "Objectives"),
                bullets(objectives, language),
                language.choose("用户场景", "User Scenarios"),
                bullets(userScenarios, language),
                language.choose("必需能力", "Required Capabilities"),
                bullets(requiredCapabilities, language),
                language.choose("可选增强", "Optional Enhancements"),
                bullets(optionalCapabilities, language),
                language.choose("非功能要求", "Non-Functional Requirements"),
                bullets(nonFunctionalRequirements, language),
                language.choose("验收标准", "Acceptance Criteria"),
                bullets(acceptanceCriteria, language),
                language.choose("非目标", "Non-Goals"),
                bullets(nonGoals, language)
        ).trim();
    }

    public List<RequirementReference> bindingRequirements() {
        return requirementReferences;
    }

    public List<RequirementReference> planningCoverageRequirements() {
        return bindingRequirements().stream()
                .filter(RequirementReference::requiresPlanningCoverage)
                .toList();
    }

    public String requirementCatalogMarkdown(DocumentLanguage language) {
        List<RequirementReference> references = bindingRequirements();
        if (references.isEmpty()) {
            return language.choose("- (无绑定能力引用)", "- (no binding requirement references)");
        }
        StringBuilder builder = new StringBuilder();
        for (RequirementReference reference : references) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(reference.id())
                    .append(" [")
                    .append(reference.category())
                    .append(", ")
                    .append(reference.obligation().markdownLabel(language))
                    .append("]: ")
                    .append(reference.text());
        }
        return builder.toString();
    }

    public boolean containsBindingRequirementId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return bindingRequirements().stream()
                .anyMatch(reference -> reference.id().equalsIgnoreCase(id.trim()));
    }

    private static int appendReferences(
            List<RequirementReference> target,
            String prefix,
            String category,
            List<String> items,
            CoverageObligation obligation,
            int startIndex
    ) {
        if (items == null) {
            return startIndex;
        }
        int index = startIndex;
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            target.add(new RequirementReference(prefix + "-" + index++, category, item.trim(), obligation));
        }
        return index;
    }

    private static List<RequirementReference> deriveRequirementReferences(
            List<String> requiredCapabilities,
            List<String> optionalCapabilities,
            List<String> acceptanceCriteria
    ) {
        List<RequirementReference> derived = new ArrayList<>();
        int nextCapabilityIndex = appendReferences(
                derived,
                "CAP",
                "required-capability",
                requiredCapabilities,
                CoverageObligation.PLANNING_REQUIRED,
                1
        );
        appendReferences(
                derived,
                "CAP",
                "optional-capability",
                optionalCapabilities,
                CoverageObligation.OPTIONAL,
                nextCapabilityIndex
        );
        appendReferences(
                derived,
                "ACC",
                "acceptance-criterion",
                acceptanceCriteria,
                CoverageObligation.FINAL_ACCEPTANCE,
                1
        );
        return List.copyOf(derived);
    }

    private static List<RequirementReference> normalizeRequirementReferences(List<RequirementReference> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        Map<String, RequirementReference> normalized = new LinkedHashMap<>();
        for (RequirementReference reference : references) {
            if (reference == null || reference.id() == null || reference.id().isBlank()) {
                continue;
            }
            String normalizedId = reference.id().trim().toUpperCase(Locale.ROOT);
            RequirementReference normalizedReference = new RequirementReference(
                    reference.id().trim(),
                    reference.category() == null ? "" : reference.category().trim(),
                    reference.text() == null ? "" : reference.text().trim(),
                    reference.obligation()
            );
            RequirementReference existing = normalized.get(normalizedId);
            if (existing == null) {
                normalized.put(normalizedId, normalizedReference);
                continue;
            }
            normalized.put(normalizedId, new RequirementReference(
                    existing.id(),
                    existing.category().isBlank() ? normalizedReference.category() : existing.category(),
                    existing.text().isBlank() ? normalizedReference.text() : existing.text(),
                    CoverageObligation.merge(existing.obligation(), normalizedReference.obligation())
            ));
        }
        return List.copyOf(normalized.values());
    }

    private static List<String> freezeTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String bullets(List<String> items, DocumentLanguage language) {
        if (items == null || items.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        return items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .map(item -> "- " + item)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(PlaceholderValues.bulletNone(language));
    }
}
