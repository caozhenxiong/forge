package devflow.agent.quality;

import java.util.List;

/**
 * 基于质量账本中的体验类能力覆盖做 gate 判定。
 */
public final class ExperienceGateEvaluator {

    public ExperienceGateOutcome evaluate(QualityLedger qualityLedger) {
        if (qualityLedger == null || qualityLedger.coverageLedger() == null || qualityLedger.coverageLedger().entries().isEmpty()) {
            return ExperienceGateOutcome.pass();
        }
        List<CoverageLedgerEntry> missing = qualityLedger.coverageLedger().entries().stream()
                .filter(entry -> entry != null && entry.required() && entry.surface() != null && entry.surface().isExperienceSurface())
                .filter(entry -> entry.status() != CoverageLedgerStatus.COVERED)
                .toList();
        if (missing.isEmpty()) {
            return ExperienceGateOutcome.pass();
        }
        String missingSurfaces = missing.stream()
                .map(entry -> entry.surface().wireValue())
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return new ExperienceGateOutcome(
                false,
                "测试阶段缺少关键体验能力的通过证据。",
                "补齐缺失的体验能力覆盖并重新执行测试。",
                "missingExperienceCoverage=" + missingSurfaces
        );
    }
}
