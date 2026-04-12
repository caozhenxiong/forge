package devflow.agent.quality;

/**
 * 单个能力项在当前轮中的覆盖要求。
 */
public record CapabilityMatrixEntry(
        String capabilityId,
        CapabilityExpectation expectation,
        boolean requiresObservationTarget,
        String observationTargetId,
        boolean requiresObservableStateChange,
        String rationale
) {

    public CapabilityMatrixEntry {
        capabilityId = CapabilityIds.normalize(capabilityId);
        expectation = expectation == null ? CapabilityExpectation.OPTIONAL : expectation;
        observationTargetId = CapabilityIds.normalize(observationTargetId);
        rationale = rationale == null ? "" : rationale;
    }

    public boolean required() {
        return expectation.required();
    }

    public boolean targetsBuiltinObservationSurface() {
        return requiresObservationTarget && CapabilityIds.isBuiltinObservationTarget(observationTargetId);
    }

    public CapabilityMatrixEntry(
            String capabilityId,
            CapabilityExpectation expectation,
            boolean requiresObservableStateChange,
            String rationale
    ) {
        this(
                capabilityId,
                expectation,
                CapabilityIds.isBuiltinObservationTarget(capabilityId),
                CapabilityIds.isBuiltinObservationTarget(capabilityId) ? capabilityId : "",
                requiresObservableStateChange,
                rationale
        );
    }
}
