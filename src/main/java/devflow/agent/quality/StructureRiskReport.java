package devflow.agent.quality;

import devflow.agent.i18n.DocumentLanguage;

/**
 * 结构风险报告。
 */
public record StructureRiskReport(
        StructureRiskLevel hostDocumentRisk,
        StructureRiskLevel embeddedLogicRisk,
        StructureRiskLevel moduleBoundaryRisk,
        boolean preferLogicExternalization,
        boolean justificationRequired
) {

    public StructureRiskReport {
        hostDocumentRisk = hostDocumentRisk == null ? StructureRiskLevel.LOW : hostDocumentRisk;
        embeddedLogicRisk = embeddedLogicRisk == null ? StructureRiskLevel.LOW : embeddedLogicRisk;
        moduleBoundaryRisk = moduleBoundaryRisk == null ? StructureRiskLevel.LOW : moduleBoundaryRisk;
    }

    public static StructureRiskReport low() {
        return new StructureRiskReport(
                StructureRiskLevel.LOW,
                StructureRiskLevel.LOW,
                StructureRiskLevel.LOW,
                false,
                false
        );
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                - hostDocumentRisk: %s
                - embeddedLogicRisk: %s
                - moduleBoundaryRisk: %s
                - preferLogicExternalization: %s
                - justificationRequired: %s
                """.formatted(
                hostDocumentRisk,
                embeddedLogicRisk,
                moduleBoundaryRisk,
                preferLogicExternalization,
                justificationRequired
        ).trim();
    }
}
