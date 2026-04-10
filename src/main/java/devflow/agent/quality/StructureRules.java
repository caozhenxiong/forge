package devflow.agent.quality;

/**
 * 结构规则。
 */
public record StructureRules(
        boolean preferLogicExternalization,
        boolean blockOnUnjustifiedEmbeddedDominance,
        StructureRiskLevel maxHostDocumentRisk
) {
}
