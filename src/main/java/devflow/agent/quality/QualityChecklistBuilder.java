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
        if (plan.structurePolicy().preferLogicExternalization() && plan.structureRiskReport().preferLogicExternalization()) {
            structureChecks.add("主要交互逻辑应优先外提到独立运行脚本/模块，而不是继续堆在宿主文档里。");
        }
        if (plan.structurePolicy().blockOnUnjustifiedEmbeddedDominance()
                && plan.structureRiskReport().justificationRequired()) {
            structureChecks.add("如果继续保留嵌入式主逻辑，必须给出明确结构性 justification。");
        }

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
