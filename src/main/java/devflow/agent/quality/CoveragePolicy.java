package devflow.agent.quality;

/**
 * 覆盖策略。
 */
public record CoveragePolicy(
        int minimumRequiredCases,
        boolean requireCapabilityBackfill,
        boolean requireObservableStateChangeForInteractiveCases
) {
}
