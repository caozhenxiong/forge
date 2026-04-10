package devflow.agent.quality;

/**
 * 根据特征画像推导结构风险。
 */
public final class StructureRiskAnalyzer {

    public StructureRiskReport analyze(FeatureProfile profile, StructureRules rules) {
        if (profile == null || rules == null) {
            return StructureRiskReport.low();
        }
        StructureRiskLevel hostRisk = StructureRiskLevel.LOW;
        StructureRiskLevel embeddedRisk = StructureRiskLevel.LOW;
        StructureRiskLevel boundaryRisk = StructureRiskLevel.LOW;
        boolean preferLogicExternalization = false;
        boolean justificationRequired = false;

        if (profile.hasEmbeddedLogic()) {
            hostRisk = StructureRiskLevel.MEDIUM;
            embeddedRisk = StructureRiskLevel.MEDIUM;
            boundaryRisk = StructureRiskLevel.MEDIUM;
            preferLogicExternalization = rules.preferLogicExternalization();
        }
        if (profile.hasEmbeddedLogic() && (profile.hasDiscreteUserInput() || profile.hasCanvasSurface() || profile.hasBackgroundLoop())) {
            hostRisk = StructureRiskLevel.HIGH;
            embeddedRisk = StructureRiskLevel.HIGH;
            boundaryRisk = StructureRiskLevel.HIGH;
            preferLogicExternalization = rules.preferLogicExternalization();
            justificationRequired = rules.blockOnUnjustifiedEmbeddedDominance();
        }
        if (profile.hasExternalLogicModule()) {
            embeddedRisk = StructureRiskLevel.LOW;
            boundaryRisk = profile.hasDiscreteUserInput() || profile.hasCanvasSurface()
                    ? StructureRiskLevel.LOW
                    : boundaryRisk;
        }
        return new StructureRiskReport(
                hostRisk,
                embeddedRisk,
                boundaryRisk,
                preferLogicExternalization,
                justificationRequired
        );
    }
}
