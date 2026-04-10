package devflow.agent.quality;

import devflow.agent.i18n.DocumentLanguage;

/**
 * 质量规则主线的统一账本。
 */
public record QualityLedger(
        StructureRiskReport structureRiskReport,
        CapabilityMatrix capabilityMatrix,
        CoverageLedger coverageLedger
) {

    public QualityLedger {
        structureRiskReport = structureRiskReport == null ? StructureRiskReport.low() : structureRiskReport;
        capabilityMatrix = capabilityMatrix == null ? CapabilityMatrix.empty() : capabilityMatrix;
        coverageLedger = coverageLedger == null ? CoverageLedger.empty() : coverageLedger;
    }

    public static QualityLedger empty() {
        return new QualityLedger(StructureRiskReport.low(), CapabilityMatrix.empty(), CoverageLedger.empty());
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ## %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s
                """.formatted(
                language.choose("质量账本", "Quality Ledger"),
                language.choose("结构风险", "Structure Risk"),
                structureRiskReport.toMarkdown(language),
                language.choose("能力矩阵", "Capability Matrix"),
                capabilityMatrix.toMarkdown(language),
                language.choose("覆盖账本", "Coverage Ledger"),
                coverageLedger.toMarkdown(language)
        ).trim();
    }
}
