package devflow.agent.quality;

/**
 * 体验 gate 结果。
 */
public record ExperienceGateOutcome(
        boolean passed,
        String summary,
        String changeRequest,
        String evidence
) {

    public static ExperienceGateOutcome pass() {
        return new ExperienceGateOutcome(true, "", "", "");
    }
}
