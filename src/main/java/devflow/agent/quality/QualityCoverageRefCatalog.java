package devflow.agent.quality;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.LinkedHashSet;
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
        if (qualityPlan == null || qualityPlan.qualityIntent().requiredCapabilityIds().isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        for (String capabilityId : qualityPlan.qualityIntent().requiredCapabilityIds()) {
            refs.add(referenceId(capabilityId));
        }
        return Set.copyOf(refs);
    }

    public static String renderCatalog(QualityPlan qualityPlan, DocumentLanguage language) {
        if (qualityPlan == null || qualityPlan.qualityIntent().requiredCapabilityIds().isEmpty()) {
            return PlaceholderValues.none(language);
        }
        StringBuilder builder = new StringBuilder();
        Set<String> requiredQualitySurfaces = qualityPlan.qualityIntent().requiredCapabilityIds();
        for (CapabilityMatrixEntry entry : qualityPlan.capabilityMatrix().entries()) {
            if (entry == null || entry.capabilityId().isBlank() || !entry.required()
                    || !requiredQualitySurfaces.contains(entry.capabilityId())) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(referenceId(entry.capabilityId()))
                    .append(": ")
                    .append(entry.capabilityId())
                    .append(" [")
                    .append(entry.expectation().name())
                    .append("]");
            if (entry.rationale() != null && !entry.rationale().isBlank()) {
                builder.append(" - ").append(entry.rationale().trim());
            }
        }
        return builder.isEmpty() ? PlaceholderValues.none(language) : builder.toString();
    }
}
