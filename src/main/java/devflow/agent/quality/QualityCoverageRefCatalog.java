package devflow.agent.quality;

import devflow.agent.context.CoverageObligation;
import devflow.agent.context.RequirementReference;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一维护质量能力覆盖引用。
 *
 * <p>implementation planning 仍然通过 `coverageRefs` 为子任务分配责任。
 * 这层把 required capability surface 映射成稳定的 `QCAP-*` 引用，避免 planner /
 * gate / artifact 各自重复拼接字符串。
 */
public final class QualityCoverageRefCatalog {

    private static final String PREFIX = "QCAP-";

    private QualityCoverageRefCatalog() {
    }

    public static String referenceId(String capabilityId) {
        String normalized = CapabilityIds.normalize(capabilityId);
        if (normalized.isBlank()) {
            return "";
        }
        return PREFIX + CapabilityIds.toReferenceSuffix(normalized);
    }

    public static String fromReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return "";
        }
        String normalized = referenceId.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.startsWith(PREFIX)) {
            return "";
        }
        return CapabilityIds.fromReferenceSuffix(normalized.substring(PREFIX.length()));
    }

    public static boolean supports(String referenceId) {
        return !fromReferenceId(referenceId).isBlank();
    }

    public static Set<String> requiredReferenceIds(QualityPlan qualityPlan) {
        return requiredReferences(qualityPlan).stream()
                .map(RequirementReference::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public static List<RequirementReference> requiredReferences(QualityPlan qualityPlan) {
        if (qualityPlan == null || qualityPlan.qualityIntent().requiredCapabilityIds().isEmpty()) {
            return List.of();
        }
        List<RequirementReference> references = new ArrayList<>();
        Set<String> requiredQualitySurfaces = qualityPlan.qualityIntent().requiredCapabilityIds();
        for (CapabilityMatrixEntry entry : qualityPlan.capabilityMatrix().entries()) {
            if (entry == null || entry.capabilityId().isBlank() || !entry.required()
                    || !requiredQualitySurfaces.contains(entry.capabilityId())) {
                continue;
            }
            references.add(new RequirementReference(
                    referenceId(entry.capabilityId()),
                    "quality-capability",
                    renderReferenceText(entry),
                    CoverageObligation.PLANNING_REQUIRED
            ));
        }
        return List.copyOf(references);
    }

    public static String renderCatalog(QualityPlan qualityPlan, DocumentLanguage language) {
        List<RequirementReference> references = requiredReferences(qualityPlan);
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
                    .append(": ")
                    .append(reference.text());
        }
        return builder.isEmpty() ? PlaceholderValues.none(language) : builder.toString();
    }

    private static String renderReferenceText(CapabilityMatrixEntry entry) {
        StringBuilder builder = new StringBuilder()
                .append(entry.capabilityId())
                .append(" [")
                .append(entry.expectation().name())
                .append("]");
        if (entry.rationale() != null && !entry.rationale().isBlank()) {
            builder.append(" - ").append(entry.rationale().trim());
        }
        return builder.toString();
    }
}
