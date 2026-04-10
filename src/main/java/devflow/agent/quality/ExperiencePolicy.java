package devflow.agent.quality;

/**
 * 体验策略。
 */
public record ExperiencePolicy(
        boolean promoteTimedProgressionCoverageFromFeatureProfile,
        boolean gateOnMissingRequiredExperienceCoverage
) {
}
