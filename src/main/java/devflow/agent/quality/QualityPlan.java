package devflow.agent.quality;

import devflow.agent.i18n.DocumentLanguage;

/**
 * 当前轮质量计划。
 */
public record QualityPlan(
        FeatureProfile featureProfile,
        QualityIntent qualityIntent,
        StructureRiskReport structureRiskReport,
        StructurePolicy structurePolicy,
        CoveragePolicy coveragePolicy,
        ExperiencePolicy experiencePolicy,
        CapabilityMatrix capabilityMatrix,
        QualityChecklist qualityChecklist
) {

    public QualityPlan {
        featureProfile = featureProfile == null ? new FeatureProfile(false, false, false, false, false, false, false, false, false) : featureProfile;
        qualityIntent = qualityIntent == null ? QualityIntent.empty() : qualityIntent;
        structureRiskReport = structureRiskReport == null ? StructureRiskReport.low() : structureRiskReport;
        capabilityMatrix = capabilityMatrix == null ? CapabilityMatrix.empty() : capabilityMatrix;
        qualityChecklist = qualityChecklist == null ? QualityChecklist.empty() : qualityChecklist;
    }

    public static QualityPlan empty() {
        QualityRules rules = new QualityRulesLoader().loadDefaults();
        return new QualityPlan(
                new FeatureProfile(false, false, false, false, false, false, false, false, false),
                QualityIntent.empty(),
                StructureRiskReport.low(),
                new StructurePolicy(
                        rules.structureRules().preferLogicExternalization(),
                        rules.structureRules().blockOnUnjustifiedEmbeddedDominance(),
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
                CapabilityMatrix.empty(),
                QualityChecklist.empty()
        );
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ## %s

                ### %s
                - hasHtmlEntry: %s
                - hasExternalLogicModule: %s
                - hasEmbeddedLogic: %s
                - hasDiscreteUserInput: %s
                - hasCanvasSurface: %s
                - hasVisibleRuntimeSurface: %s
                - hasBackgroundLoop: %s
                - hasTimedProgression: %s
                - hasPerformanceRequirements: %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s
                """.formatted(
                language.choose("质量计划", "Quality Plan"),
                language.choose("特征画像", "Feature Profile"),
                featureProfile.hasHtmlEntry(),
                featureProfile.hasExternalLogicModule(),
                featureProfile.hasEmbeddedLogic(),
                featureProfile.hasDiscreteUserInput(),
                featureProfile.hasCanvasSurface(),
                featureProfile.hasVisibleRuntimeSurface(),
                featureProfile.hasBackgroundLoop(),
                featureProfile.hasTimedProgression(),
                featureProfile.hasPerformanceRequirements(),
                language.choose("质量意图", "Quality Intent"),
                qualityIntent.toMarkdown(language),
                language.choose("结构风险", "Structure Risk"),
                structureRiskReport.toMarkdown(language),
                language.choose("能力矩阵", "Capability Matrix"),
                capabilityMatrix.toMarkdown(language),
                language.choose("质量清单", "Quality Checklist"),
                qualityChecklist.toMarkdown(language)
        ).trim();
    }
}
