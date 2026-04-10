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

    public static String referenceId(CapabilitySurface surface) {
        if (surface == null) {
            return "";
        }
        return PREFIX + surface.name();
    }

    public static CapabilitySurface fromReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return null;
        }
        String normalized = referenceId.trim().toUpperCase();
        if (!normalized.startsWith(PREFIX)) {
            return null;
        }
        String suffix = normalized.substring(PREFIX.length());
        try {
            return CapabilitySurface.valueOf(suffix);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean supports(String referenceId) {
        return fromReferenceId(referenceId) != null;
    }

    public static Set<String> requiredReferenceIds(QualityPlan qualityPlan) {
        if (qualityPlan == null || qualityPlan.qualityIntent().requiredCapabilitySurfaces().isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        for (CapabilitySurface surface : qualityPlan.qualityIntent().requiredCapabilitySurfaces()) {
            refs.add(referenceId(surface));
        }
        return Set.copyOf(refs);
    }

    public static String renderCatalog(QualityPlan qualityPlan, DocumentLanguage language) {
        if (qualityPlan == null || qualityPlan.qualityIntent().requiredCapabilitySurfaces().isEmpty()) {
            return PlaceholderValues.none(language);
        }
        StringBuilder builder = new StringBuilder();
        Set<CapabilitySurface> requiredQualitySurfaces = qualityPlan.qualityIntent().requiredCapabilitySurfaces();
        for (CapabilityMatrixEntry entry : qualityPlan.capabilityMatrix().entries()) {
            if (entry == null || entry.surface() == null || !entry.required()
                    || !requiredQualitySurfaces.contains(entry.surface())) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(referenceId(entry.surface()))
                    .append(": ")
                    .append(entry.surface().wireValue())
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
