package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.ArrayList;
import java.util.List;

public record ProductContract(
        List<String> objectives,
        List<String> userScenarios,
        List<String> requiredCapabilities,
        List<String> nonFunctionalRequirements,
        List<String> acceptanceCriteria,
        List<String> nonGoals,
        List<RequirementReference> requirementReferences
) {

    public ProductContract {
        objectives = freezeTextList(objectives);
        userScenarios = freezeTextList(userScenarios);
        requiredCapabilities = freezeTextList(requiredCapabilities);
        nonFunctionalRequirements = freezeTextList(nonFunctionalRequirements);
        acceptanceCriteria = freezeTextList(acceptanceCriteria);
        nonGoals = freezeTextList(nonGoals);
        requirementReferences = normalizeRequirementReferences(requirementReferences, requiredCapabilities, acceptanceCriteria);
    }

    /**
     * 兼容旧的六段式 ProductContract 构造方式。
     *
     * <p>旧调用方只提供 prose 列表时，这里会在数据模型层补齐稳定 requirement refs，
     * 避免结构化 PRODUCT_CONTRACT 持久化后仍然丢失覆盖锚点。
     */
    public ProductContract(
            List<String> objectives,
            List<String> userScenarios,
            List<String> requiredCapabilities,
            List<String> nonFunctionalRequirements,
            List<String> acceptanceCriteria,
            List<String> nonGoals
    ) {
        this(objectives, userScenarios, requiredCapabilities, nonFunctionalRequirements, acceptanceCriteria, nonGoals, List.of());
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
                """.formatted(
                language.choose("产品契约（参考）", "Product Contract (Reference Only)"),
                language.choose("以下内容用于帮助后续节点理解产品目标与验收语义，但不单独构成绑定硬约束；绑定约束以 Execution Contract 与 Source Metadata 中的 hard.* 为准。", "The following content helps downstream nodes understand product intent and acceptance semantics, but does not by itself create binding hard constraints; binding constraints come from the Execution Contract and Source Metadata hard.* entries."),
                language.choose("目标", "Objectives"),
                bullets(objectives, language),
                language.choose("用户场景", "User Scenarios"),
                bullets(userScenarios, language),
                language.choose("必需能力", "Required Capabilities"),
                bullets(requiredCapabilities, language),
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
                .filter(RequirementReference::planningRequired)
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
                    .append(reference.planningRequired()
                            ? language.choose(", planning-required", ", planning-required")
                            : language.choose(", final-acceptance", ", final-acceptance"))
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

    private void appendReferences(
            List<RequirementReference> target,
            String prefix,
            String category,
            List<String> items,
            boolean planningRequired
    ) {
        if (items == null) {
            return;
        }
        int index = 1;
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String normalized = item.trim();
            boolean effectivePlanningRequired = planningRequired;
            target.add(new RequirementReference(prefix + "-" + index++, category, normalized, effectivePlanningRequired));
        }
    }

    /**
     * requirement refs 是后续 planning / test / review 的稳定锚点。
     *
     * <p>如果这里只在读取时临时回填，而对象本身仍然保存空列表，
     * 那么写回 PRODUCT_CONTRACT 结构化块时仍会把 refs 落成空值，
     * 下游阶段就只能重新从 prose 猜覆盖范围。这里直接在数据模型层补齐，
     * 保证持久化后的 contract 本身就是完整的权威载体。
     */
    private List<RequirementReference> normalizeRequirementReferences(
            List<RequirementReference> references,
            List<String> normalizedRequiredCapabilities,
            List<String> normalizedAcceptanceCriteria
    ) {
        List<RequirementReference> explicitReferences = references == null
                ? List.of()
                : references.stream()
                .filter(reference -> reference != null && reference.id() != null && !reference.id().isBlank())
                .map(reference -> new RequirementReference(
                        reference.id().trim(),
                        reference.category() == null ? "" : reference.category().trim(),
                        reference.text() == null ? "" : reference.text().trim(),
                        reference.planningRequired()
                ))
                .toList();
        if (!explicitReferences.isEmpty()) {
            return List.copyOf(explicitReferences);
        }
        List<RequirementReference> fallbackReferences = new ArrayList<>();
        appendReferences(fallbackReferences, "CAP", "required-capability", normalizedRequiredCapabilities, true);
        appendReferences(fallbackReferences, "ACC", "acceptance-criterion", normalizedAcceptanceCriteria, false);
        return List.copyOf(fallbackReferences);
    }

    private List<String> freezeTextList(List<String> values) {
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
