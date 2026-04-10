package devflow.agent.quality;

import devflow.agent.validation.ProjectFingerprint;

/**
 * 基于结构风险和当前项目布局做 gate 判定。
 */
public final class StructureGateEvaluator {

    public StructureGateOutcome evaluate(ProjectFingerprint fingerprint, QualityPlan qualityPlan) {
        if (qualityPlan == null) {
            return StructureGateOutcome.pass();
        }
        boolean blockOnEmbeddedDominance = qualityPlan.structurePolicy().blockOnUnjustifiedEmbeddedDominance()
                && qualityPlan.featureProfile().hasEmbeddedLogic()
                && (qualityPlan.structureRiskReport().justificationRequired()
                || qualityPlan.structureRiskReport().embeddedLogicRisk().atLeast(StructureRiskLevel.HIGH)
                || qualityPlan.structureRiskReport().hostDocumentRisk().atLeast(qualityPlan.structurePolicy().maxHostDocumentRisk()));
        if (!blockOnEmbeddedDominance) {
            return StructureGateOutcome.pass();
        }
        return new StructureGateOutcome(
                false,
                "当前交互型 HTML 交付仍以宿主文档内联逻辑为主，结构风险过高。",
                "将主要逻辑外提到独立脚本模块，或提供明确的结构性 justification 后再提交评审。",
                qualityPlan.structureRiskReport().toMarkdown(devflow.agent.i18n.DocumentLanguage.ZH)
        );
    }
}
