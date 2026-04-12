package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.QualityCoverageRefCatalog;
import devflow.agent.quality.QualityPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * implementation / test / review 共用的唯一 coverage 引用目录。
 *
 * <p>它把产品 requirement refs 与质量 required refs 合并成一份权威目录：
 * 1. planner 只看到这一份目录；
 * 2. gate 只校验这一份目录；
 * 3. test prompt 也只引用这一份目录。
 *
 * <p>这样可以彻底去掉“产品 refs 一套、质量 refs 一套、再靠局部去重补丁拼起来”的双轨状态。
 */
public record AuthoritativeCoverageCatalog(List<RequirementReference> references) {

    public AuthoritativeCoverageCatalog {
        references = normalizeReferences(references);
    }

    public static AuthoritativeCoverageCatalog from(ProductContract productContract, QualityPlan qualityPlan) {
        LinkedHashMap<String, RequirementReference> merged = new LinkedHashMap<>();
        Map<String, String> normalizedCapabilityOwners = new LinkedHashMap<>();
        if (productContract != null) {
            for (RequirementReference reference : productContract.bindingRequirements()) {
                RequirementReference normalizedReference = normalizeReference(reference);
                if (normalizedReference == null) {
                    continue;
                }
                String idKey = normalizeReferenceId(normalizedReference.id());
                merged.putIfAbsent(idKey, normalizedReference);
                normalizedCapabilityOwners.putIfAbsent(CapabilityIds.normalize(normalizedReference.id()), idKey);
            }
        }
        for (RequirementReference reference : QualityCoverageRefCatalog.requiredReferences(qualityPlan)) {
            RequirementReference normalizedReference = normalizeReference(reference);
            if (normalizedReference == null) {
                continue;
            }
            String capabilityId = QualityCoverageRefCatalog.fromReferenceId(normalizedReference.id());
            String existingOwner = normalizedCapabilityOwners.get(CapabilityIds.normalize(capabilityId));
            if (existingOwner != null) {
                RequirementReference existing = merged.get(existingOwner);
                if (existing != null && !existing.requiresPlanningCoverage()) {
                    merged.put(existingOwner, new RequirementReference(
                            existing.id(),
                            existing.category(),
                            existing.text(),
                            CoverageObligation.merge(existing.obligation(), normalizedReference.obligation())
                    ));
                }
                continue;
            }
            merged.putIfAbsent(normalizeReferenceId(normalizedReference.id()), normalizedReference);
        }
        return new AuthoritativeCoverageCatalog(List.copyOf(merged.values()));
    }

    public List<RequirementReference> planningRequiredReferences() {
        return references.stream()
                .filter(RequirementReference::requiresPlanningCoverage)
                .toList();
    }

    public boolean containsReferenceId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String normalizedId = normalizeReferenceId(id);
        return references.stream()
                .map(RequirementReference::id)
                .map(AuthoritativeCoverageCatalog::normalizeReferenceId)
                .anyMatch(normalizedId::equals);
    }

    public Set<String> allowedReferenceIds() {
        return references.stream()
                .map(RequirementReference::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public String toMarkdown(DocumentLanguage language) {
        if (references.isEmpty()) {
            return PlaceholderValues.none(language);
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

    private static List<RequirementReference> normalizeReferences(List<RequirementReference> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, RequirementReference> normalized = new LinkedHashMap<>();
        for (RequirementReference reference : references) {
            RequirementReference normalizedReference = normalizeReference(reference);
            if (normalizedReference == null) {
                continue;
            }
            String key = normalizeReferenceId(normalizedReference.id());
            RequirementReference existing = normalized.get(key);
            if (existing == null) {
                normalized.put(key, normalizedReference);
                continue;
            }
            normalized.put(key, new RequirementReference(
                    existing.id(),
                    existing.category().isBlank() ? normalizedReference.category() : existing.category(),
                    existing.text().isBlank() ? normalizedReference.text() : existing.text(),
                    CoverageObligation.merge(existing.obligation(), normalizedReference.obligation())
            ));
        }
        return List.copyOf(normalized.values());
    }

    private static RequirementReference normalizeReference(RequirementReference reference) {
        if (reference == null || reference.id() == null || reference.id().isBlank()) {
            return null;
        }
        return new RequirementReference(
                reference.id().trim(),
                reference.category() == null ? "" : reference.category().trim(),
                reference.text() == null ? "" : reference.text().trim(),
                reference.obligation()
        );
    }

    private static String normalizeReferenceId(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
