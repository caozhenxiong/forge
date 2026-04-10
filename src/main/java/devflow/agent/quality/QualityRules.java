package devflow.agent.quality;

/**
 * 仓库级质量规则。
 */
public record QualityRules(
        StructureRules structureRules,
        VerificationRules verificationRules,
        ExperienceRules experienceRules
) {
}
