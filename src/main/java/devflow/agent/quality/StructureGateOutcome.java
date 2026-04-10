package devflow.agent.quality;

/**
 * 结构 gate 结果。
 */
public record StructureGateOutcome(
        boolean passed,
        String summary,
        String changeRequest,
        String evidence
) {

    public static StructureGateOutcome pass() {
        return new StructureGateOutcome(true, "", "", "");
    }
}
