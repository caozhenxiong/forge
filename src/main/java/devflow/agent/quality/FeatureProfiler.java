package devflow.agent.quality;

import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.executor.RuntimeSnapshot;
import devflow.agent.validation.ProjectFingerprint;
import java.util.Locale;

/**
 * 根据项目、契约和运行时快照提取稳定特征。
 */
public final class FeatureProfiler {

    public FeatureProfile profile(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            ValidationMetadata validationMetadata,
            RuntimeSnapshot runtimeSnapshot
    ) {
        ExecutionContract executionContract = contractView == null ? null : contractView.executionContract();
        boolean hasHtmlEntry = fingerprint != null && fingerprint.hasResolvedHtmlEntry();
        boolean hasExternalLogicModule = fingerprint != null && (fingerprint.hasJavaScript() || fingerprint.hasTypeScript());
        boolean hasEmbeddedLogic = hasHtmlEntry && !hasExternalLogicModule;
        boolean hasDiscreteUserInput = hasInteractiveSelectors(runtimeSnapshot)
                || signalPresent(executionContract, "interactive")
                || signalPresent(executionContract, "play-surface-renders");
        boolean hasCanvasSurface = runtimeSnapshot != null && runtimeSnapshot.canvasCount() > 0;
        boolean hasVisibleRuntimeSurface = hasHtmlEntry
                || hasCanvasSurface
                || signalPresent(executionContract, "runtime-surface-renders")
                || signalPresent(executionContract, "ui-renders")
                || signalPresent(executionContract, "play-surface-renders");
        boolean hasBackgroundLoop = hasCanvasSurface && hasDiscreteUserInput;
        boolean hasTimedProgression = hasBackgroundLoop || signalPresent(executionContract, "runtime-starts");
        boolean hasPerformanceRequirements = validationMetadata != null
                && (validationMetadata.performanceMeasurementRequired()
                || validationMetadata.pageLoadMaxMs() != null
                || validationMetadata.interactionMaxMs() != null);
        return new FeatureProfile(
                hasHtmlEntry,
                hasExternalLogicModule,
                hasEmbeddedLogic,
                hasDiscreteUserInput,
                hasCanvasSurface,
                hasVisibleRuntimeSurface,
                hasBackgroundLoop,
                hasTimedProgression,
                hasPerformanceRequirements
        );
    }

    private boolean hasInteractiveSelectors(RuntimeSnapshot runtimeSnapshot) {
        if (runtimeSnapshot == null || runtimeSnapshot.selectors() == null || runtimeSnapshot.selectors().isEmpty()) {
            return false;
        }
        return runtimeSnapshot.selectors().stream().anyMatch(selector ->
                selector != null
                        && !selector.isBlank()
                        && (selector.startsWith("#") || selector.startsWith(".") || "button".equalsIgnoreCase(selector))
        );
    }

    private boolean signalPresent(ExecutionContract executionContract, String signalFragment) {
        if (executionContract == null || signalFragment == null || signalFragment.isBlank()) {
            return false;
        }
        return executionContract.acceptanceSignals().stream().anyMatch(signal -> signal.contains(signalFragment.toLowerCase(Locale.ROOT)));
    }
}
