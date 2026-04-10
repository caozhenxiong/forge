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

    /**
     * 兼容旧的六段式 ProductContract 构造方式。
     *
     * <p>这批字段主要服务人类可读文档和参考语义；如果没有显式的 requirementReferences，
     * 流程层不能再把 prose 能力列表自动升级成 planning-required 的硬覆盖要求。
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
        if (requirementReferences != null && !requirementReferences.isEmpty()) {
            return requirementReferences.stream()
                    .filter(reference -> reference != null && reference.id() != null && !reference.id().isBlank())
                    .toList();
        }
        List<RequirementReference> references = new ArrayList<>();
        appendReferences(references, "CAP", "required-capability", requiredCapabilities, false);
        appendReferences(references, "ACC", "acceptance-criterion", acceptanceCriteria, false);
        return List.copyOf(references);
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
