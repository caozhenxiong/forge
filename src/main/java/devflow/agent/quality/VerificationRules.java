package devflow.agent.quality;

import java.util.Set;

/**
 * 验证规则。
 */
public record VerificationRules(
        int minimumRequiredCases,
        boolean requirePageLoadCoverage,
        boolean requireRuntimeStabilityCoverage,
        boolean requireVisualSurfaceCoverage,
        boolean requireCapabilityBackfill,
        boolean requireObservableStateChangeForInteractiveCases,
        boolean requirePerformanceCoverageFromMetadata,
        Set<CapabilitySurface> requiredCapabilitySurfaces
) {

    public VerificationRules {
        requiredCapabilitySurfaces = requiredCapabilitySurfaces == null ? Set.of() : Set.copyOf(requiredCapabilitySurfaces);
    }
}
