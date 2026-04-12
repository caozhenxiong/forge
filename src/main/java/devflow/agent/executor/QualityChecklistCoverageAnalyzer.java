package devflow.agent.executor;

import devflow.agent.quality.QualityCoverageRefCatalog;
import devflow.agent.quality.QualityPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把 QualityChecklist 中的 required capability surface 显式升级成 planning gate。
 *
 * <p>这样质量要求会在 implementation plan 阶段就变成稳定阻断，而不是等到最终 test/review 才暴露。
 */
final class QualityChecklistCoverageAnalyzer {

    List<String> detectMissingRequiredCoverageRefs(QualityPlan qualityPlan, List<String> plannedCoverageRefs) {
        if (qualityPlan == null) {
            return List.of();
        }
        Set<String> requiredRefs = QualityCoverageRefCatalog.requiredReferenceIds(qualityPlan);
        if (requiredRefs.isEmpty()) {
            return List.of();
        }
        List<String> normalizedRefs = plannedCoverageRefs == null
                ? List.of()
                : plannedCoverageRefs.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(this::normalizeCoverageRef)
                .distinct()
                .toList();
        List<String> issues = new ArrayList<>();
        for (String requiredRef : requiredRefs) {
            if (normalizedRefs.contains(normalizeCoverageRef(requiredRef))) {
                continue;
            }
            String capabilityId = QualityCoverageRefCatalog.fromReferenceId(requiredRef);
            issues.add("当前实现计划未覆盖 quality checklist 中的 required capability surface："
                    + (capabilityId.isBlank() ? requiredRef : capabilityId)
                    + "（coverageRef=" + requiredRef + "）。");
            if (issues.size() >= 3) {
                break;
            }
        }
        return issues;
    }

    private String normalizeCoverageRef(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
