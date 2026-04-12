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

    public Set<String> build(
            FeatureProfile profile,
            QualityIntent qualityIntent,
            ContractView contractView,
            ValidationMetadata validationMetadata
    ) {
        if (profile == null) {
            return Set.of();
        }
        LinkedHashSet<String> capabilityIds = new LinkedHashSet<>();
        ExecutionContract executionContract = contractView == null ? null : contractView.executionContract();
        if (profile.hasHtmlEntry() || (executionContract != null && executionContract.entryRequired())) {
            capabilityIds.add(CapabilityIds.PAGE_LOAD);
        }
        if ((executionContract != null && executionContract.launchRequired()) || profile.hasHtmlEntry()) {
            capabilityIds.add(CapabilityIds.RUNTIME_STABILITY);
        }
        if ((executionContract != null && executionContract.surfaceRequired()) || profile.hasCanvasSurface()) {
            capabilityIds.add(CapabilityIds.PRIMARY_VISUAL_SURFACE);
        }
        if (profile.hasDiscreteUserInput()) {
            capabilityIds.add(CapabilityIds.PRIMARY_INTERACTION);
        }
        if (validationMetadata != null && validationMetadata.pageLoadMaxMs() != null) {
            capabilityIds.add(CapabilityIds.PERFORMANCE_LOAD);
        }
        if (validationMetadata != null && validationMetadata.interactionMaxMs() != null) {
            capabilityIds.add(CapabilityIds.PERFORMANCE_INTERACTION);
        }
        if (qualityIntent != null) {
            capabilityIds.addAll(qualityIntent.requiredCapabilityIds());
        }
        return Set.copyOf(capabilityIds);
    }
}
