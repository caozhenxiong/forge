package devflow.agent.quality;

import devflow.agent.context.ContractView;
import devflow.agent.context.ValidationMetadata;
import java.util.Set;

/**
 * 把规则、特征和结构化契约收成当前轮质量计划。
 */
public final class QualityPolicyResolver {

    private final CapabilitySurfaceBuilder capabilitySurfaceBuilder = new CapabilitySurfaceBuilder();
    private final StructureRiskAnalyzer structureRiskAnalyzer = new StructureRiskAnalyzer();
    private final CapabilityMatrixBuilder capabilityMatrixBuilder = new CapabilityMatrixBuilder();
    private final QualityChecklistBuilder qualityChecklistBuilder = new QualityChecklistBuilder();

    public QualityPlan resolve(
            QualityRules rules,
            FeatureProfile profile,
            QualityIntent qualityIntent,
            ContractView contractView,
            ValidationMetadata validationMetadata
    ) {
        if (rules == null || profile == null) {
            return QualityPlan.empty();
        }
        StructureRiskReport structureRiskReport = structureRiskAnalyzer.analyze(profile, rules.structureRules());
        QualityIntent normalizedIntent = qualityIntent == null ? QualityIntent.empty() : qualityIntent;
        Set<CapabilitySurface> surfaces = capabilitySurfaceBuilder.build(profile, normalizedIntent, contractView, validationMetadata);
        CapabilityMatrix capabilityMatrix = capabilityMatrixBuilder.build(surfaces, profile, normalizedIntent, validationMetadata, rules);
        QualityPlan plan = new QualityPlan(
                profile,
                normalizedIntent,
                structureRiskReport,
                new StructurePolicy(
                        false,
                        false,
                        rules.structureRules().maxHostDocumentRisk()
                ),
                new CoveragePolicy(
                        rules.verificationRules().minimumRequiredCases(),
                        rules.verificationRules().requireCapabilityBackfill(),
                        rules.verificationRules().requireObservableStateChangeForInteractiveCases()
                ),
                new ExperiencePolicy(
                        rules.experienceRules().promoteTimedProgressionCoverageFromFeatureProfile(),
                        rules.experienceRules().gateOnMissingRequiredExperienceCoverage()
                ),
                capabilityMatrix,
                QualityChecklist.empty()
        );
        return new QualityPlan(
                plan.featureProfile(),
                plan.qualityIntent(),
                plan.structureRiskReport(),
                plan.structurePolicy(),
                plan.coveragePolicy(),
                plan.experiencePolicy(),
                plan.capabilityMatrix(),
                qualityChecklistBuilder.build(plan)
        );
    }
}
