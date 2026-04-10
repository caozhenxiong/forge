package devflow.agent.quality;

/**
 * 体验规则。
 */
public record ExperienceRules(
        boolean promoteTimedProgressionCoverageFromFeatureProfile,
        boolean gateOnMissingRequiredExperienceCoverage
) {
}
