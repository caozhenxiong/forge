package devflow.agent.quality;

import devflow.agent.context.ValidationMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 构建当前轮的能力矩阵。
 */
public final class CapabilityMatrixBuilder {

    public CapabilityMatrix build(
            Set<CapabilitySurface> surfaces,
            FeatureProfile profile,
            QualityIntent qualityIntent,
            ValidationMetadata validationMetadata,
            QualityRules rules
    ) {
        if (surfaces == null || surfaces.isEmpty() || rules == null) {
            return CapabilityMatrix.empty();
        }
        List<CapabilityMatrixEntry> entries = new ArrayList<>();
        VerificationRules verificationRules = rules.verificationRules();
        ExperienceRules experienceRules = rules.experienceRules();
        for (CapabilitySurface surface : surfaces) {
            CapabilityMatrixEntry entry = buildEntry(surface, profile, qualityIntent, validationMetadata, verificationRules, experienceRules);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new CapabilityMatrix(entries);
    }

    private CapabilityMatrixEntry buildEntry(
            CapabilitySurface surface,
            FeatureProfile profile,
            QualityIntent qualityIntent,
            ValidationMetadata validationMetadata,
            VerificationRules verificationRules,
            ExperienceRules experienceRules
    ) {
        boolean requiredByIntent = qualityIntent != null && qualityIntent.requiredCapabilitySurfaces().contains(surface);
        return switch (surface) {
            case PAGE_LOAD -> new CapabilityMatrixEntry(
                    surface,
                    verificationRules.requirePageLoadCoverage() || requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.SMOKE,
                    false,
                    "entry launchability must be validated"
            );
            case RUNTIME_STABILITY -> new CapabilityMatrixEntry(
                    surface,
                    verificationRules.requireRuntimeStabilityCoverage() || requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.SMOKE,
                    false,
                    "runtime must stay free of fatal errors"
            );
            case PRIMARY_VISUAL_SURFACE -> new CapabilityMatrixEntry(
                    surface,
                    verificationRules.requireVisualSurfaceCoverage() || requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.SMOKE,
                    false,
                    "primary visual surface must render"
            );
            case PRIMARY_INTERACTION -> new CapabilityMatrixEntry(
                    surface,
                    requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.REQUIRED,
                    verificationRules.requireObservableStateChangeForInteractiveCases(),
                    "primary user interaction must produce an observable effect"
            );
            case PAUSE_FREEZE -> new CapabilityMatrixEntry(
                    surface,
                    requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.OPTIONAL,
                    true,
                    "explicit experience contract requires pause-like freeze evidence"
            );
            case RESET_RESTORES_INITIAL_STATE -> new CapabilityMatrixEntry(
                    surface,
                    requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.OPTIONAL,
                    true,
                    "explicit experience contract requires reset-to-initial-state evidence"
            );
            case TIMED_STATE_PROGRESSION -> new CapabilityMatrixEntry(
                    surface,
                    requiredByIntent || ((experienceRules.promoteTimedProgressionCoverageFromFeatureProfile())
                            && profile != null && profile.hasTimedProgression())
                            ? CapabilityExpectation.REQUIRED
                            : CapabilityExpectation.SMOKE,
                    true,
                    "time-driven state progression must be verified"
            );
            case VISIBLE_PROGRESS_SIGNAL -> new CapabilityMatrixEntry(
                    surface,
                    requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.OPTIONAL,
                    false,
                    "explicit experience contract requires visible progress or status evidence"
            );
            case PERFORMANCE_LOAD -> new CapabilityMatrixEntry(
                    surface,
                    validationMetadata != null && validationMetadata.pageLoadMaxMs() != null
                            ? CapabilityExpectation.REQUIRED
                            : CapabilityExpectation.OPTIONAL,
                    false,
                    "page load threshold is explicitly declared"
            );
            case PERFORMANCE_INTERACTION -> new CapabilityMatrixEntry(
                    surface,
                    validationMetadata != null && validationMetadata.interactionMaxMs() != null
                            ? CapabilityExpectation.REQUIRED
                            : CapabilityExpectation.OPTIONAL,
                    false,
                    "interaction threshold is explicitly declared"
            );
        };
    }
}
