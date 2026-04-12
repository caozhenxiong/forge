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
        for (String capabilityId : plan.capabilityMatrix().requiredCapabilityIds()) {
            coverageChecks.add("必须覆盖 required capability surface: " + capabilityId);
        }

        List<String> experienceChecks = new ArrayList<>();
        for (CapabilityMatrixEntry entry : plan.capabilityMatrix().entries()) {
            if (entry != null && entry.required() && entry.requiresObservableStateChange()) {
                experienceChecks.add("必须提供体验能力通过证据: " + entry.capabilityId());
            }
        }
        return new QualityChecklist(structureChecks, coverageChecks, experienceChecks);
    }
}
