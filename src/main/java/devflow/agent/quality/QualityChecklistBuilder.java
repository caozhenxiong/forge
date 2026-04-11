package devflow.agent.quality;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建 planning / implementation / review / test 共用的质量清单。
 */
public final class QualityChecklistBuilder {

    public QualityChecklist build(QualityPlan plan) {
        if (plan == null) {
            return QualityChecklist.empty();
        }
        List<String> structureChecks = new ArrayList<>();

        List<String> coverageChecks = new ArrayList<>();
        for (CapabilitySurface surface : plan.capabilityMatrix().requiredSurfaces()) {
            coverageChecks.add("必须覆盖 required capability surface: " + surface.wireValue());
        }

        List<String> experienceChecks = new ArrayList<>();
        for (CapabilitySurface surface : plan.capabilityMatrix().requiredSurfaces()) {
            if (surface != null && surface.isExperienceSurface()) {
                experienceChecks.add("必须提供体验能力通过证据: " + surface.wireValue());
            }
        }
        return new QualityChecklist(structureChecks, coverageChecks, experienceChecks);
    }
}
