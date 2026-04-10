package devflow.agent.quality;

/**
 * 单个能力项在当前轮中的覆盖要求。
 */
public record CapabilityMatrixEntry(
        CapabilitySurface surface,
        CapabilityExpectation expectation,
        boolean requiresObservableStateChange,
        String rationale
) {

    public CapabilityMatrixEntry {
        expectation = expectation == null ? CapabilityExpectation.OPTIONAL : expectation;
        rationale = rationale == null ? "" : rationale;
    }

    public boolean required() {
        return expectation.required();
    }
}
