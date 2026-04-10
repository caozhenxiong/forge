package devflow.agent.quality;

/**
 * 结构策略。
 */
public record StructurePolicy(
        boolean preferLogicExternalization,
        boolean blockOnUnjustifiedEmbeddedDominance,
        StructureRiskLevel maxHostDocumentRisk
) {
}
