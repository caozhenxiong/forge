package devflow.agent.quality;

import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ValidationMetadata;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 从特征画像构建能力表面集合。
 */
public final class CapabilitySurfaceBuilder {

    public Set<CapabilitySurface> build(
            FeatureProfile profile,
            QualityIntent qualityIntent,
            ContractView contractView,
            ValidationMetadata validationMetadata
    ) {
        if (profile == null) {
            return Set.of();
        }
        LinkedHashSet<CapabilitySurface> surfaces = new LinkedHashSet<>();
        ExecutionContract executionContract = contractView == null ? null : contractView.executionContract();
        if (profile.hasHtmlEntry() || (executionContract != null && executionContract.entryRequired())) {
            surfaces.add(CapabilitySurface.PAGE_LOAD);
        }
        if ((executionContract != null && executionContract.launchRequired()) || profile.hasHtmlEntry()) {
            surfaces.add(CapabilitySurface.RUNTIME_STABILITY);
        }
        if ((executionContract != null && executionContract.surfaceRequired()) || profile.hasCanvasSurface()) {
            surfaces.add(CapabilitySurface.PRIMARY_VISUAL_SURFACE);
        }
        if (profile.hasDiscreteUserInput()) {
            surfaces.add(CapabilitySurface.PRIMARY_INTERACTION);
        }
        if (profile.hasTimedProgression()) {
            surfaces.add(CapabilitySurface.TIMED_STATE_PROGRESSION);
        }
        if (validationMetadata != null && validationMetadata.pageLoadMaxMs() != null) {
            surfaces.add(CapabilitySurface.PERFORMANCE_LOAD);
        }
        if (validationMetadata != null && validationMetadata.interactionMaxMs() != null) {
            surfaces.add(CapabilitySurface.PERFORMANCE_INTERACTION);
        }
        if (qualityIntent != null) {
            surfaces.addAll(qualityIntent.requiredCapabilitySurfaces());
        }
        return Set.copyOf(surfaces);
    }
}
