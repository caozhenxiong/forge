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
            Set<String> capabilityIds,
            FeatureProfile profile,
            QualityIntent qualityIntent,
            ValidationMetadata validationMetadata,
            QualityRules rules
    ) {
        if (capabilityIds == null || capabilityIds.isEmpty() || rules == null) {
            return CapabilityMatrix.empty();
        }
        List<CapabilityMatrixEntry> entries = new ArrayList<>();
        VerificationRules verificationRules = rules.verificationRules();
        for (String capabilityId : capabilityIds) {
            CapabilityMatrixEntry entry = buildEntry(capabilityId, profile, qualityIntent, validationMetadata, verificationRules);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new CapabilityMatrix(entries);
    }

    private CapabilityMatrixEntry buildEntry(
            String capabilityId,
            FeatureProfile profile,
            QualityIntent qualityIntent,
            ValidationMetadata validationMetadata,
            VerificationRules verificationRules
    ) {
        String normalizedId = CapabilityIds.normalize(capabilityId);
        boolean requiredByIntent = qualityIntent != null && qualityIntent.requiredCapabilityIds().contains(normalizedId);
        if (CapabilityIds.PAGE_LOAD.equals(normalizedId)) {
            return new CapabilityMatrixEntry(
                    normalizedId,
                    verificationRules.requirePageLoadCoverage() || requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.SMOKE,
                    false,
                    "",
                    false,
                    "entry launchability must be validated"
            );
        }
        if (CapabilityIds.RUNTIME_STABILITY.equals(normalizedId)) {
            return new CapabilityMatrixEntry(
                    normalizedId,
                    verificationRules.requireRuntimeStabilityCoverage() || requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.SMOKE,
                    false,
                    "",
                    false,
                    "runtime must stay free of fatal errors"
            );
        }
        if (CapabilityIds.PRIMARY_VISUAL_SURFACE.equals(normalizedId)) {
            return new CapabilityMatrixEntry(
                    normalizedId,
                    verificationRules.requireVisualSurfaceCoverage() || requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.SMOKE,
                    true,
                    CapabilityIds.PRIMARY_VISUAL_SURFACE,
                    false,
                    "primary visual surface must render"
            );
        }
        if (CapabilityIds.PRIMARY_INTERACTION.equals(normalizedId)) {
            return new CapabilityMatrixEntry(
                    normalizedId,
                    CapabilityExpectation.REQUIRED,
                    true,
                    CapabilityIds.PRIMARY_INTERACTION,
                    verificationRules.requireObservableStateChangeForInteractiveCases(),
                    "primary user interaction must produce an observable effect"
            );
        }
        if (CapabilityIds.PERFORMANCE_LOAD.equals(normalizedId)) {
            return new CapabilityMatrixEntry(
                    normalizedId,
                    validationMetadata != null && validationMetadata.pageLoadMaxMs() != null
                            ? CapabilityExpectation.REQUIRED
                            : CapabilityExpectation.OPTIONAL,
                    false,
                    "",
                    false,
                    "page load threshold is explicitly declared"
            );
        }
        if (CapabilityIds.PERFORMANCE_INTERACTION.equals(normalizedId)) {
            return new CapabilityMatrixEntry(
                    normalizedId,
                    validationMetadata != null && validationMetadata.interactionMaxMs() != null
                            ? CapabilityExpectation.REQUIRED
                            : CapabilityExpectation.OPTIONAL,
                    false,
                    "",
                    false,
                    "interaction threshold is explicitly declared"
            );
        }
        return buildExplicitIntentEntry(normalizedId, requiredByIntent, profile);
    }

    private CapabilityMatrixEntry buildExplicitIntentEntry(
            String capabilityId,
            boolean requiredByIntent,
            FeatureProfile profile
    ) {
        if (capabilityId.isBlank()) {
            return null;
        }
        boolean interactiveProfile = profile != null && (profile.hasDiscreteUserInput() || profile.hasTimedProgression());
        boolean requiresObservableStateChange = requiredByIntent && interactiveProfile;
        String observationTargetId = requiresObservableStateChange ? CapabilityIds.PRIMARY_INTERACTION : "";
        return new CapabilityMatrixEntry(
                capabilityId,
                requiredByIntent ? CapabilityExpectation.REQUIRED : CapabilityExpectation.OPTIONAL,
                !observationTargetId.isBlank(),
                observationTargetId,
                requiresObservableStateChange,
                "explicit quality intent requires dedicated coverage evidence"
        );
    }
}
