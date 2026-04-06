package devflow.agent.validation;

public record ValidationStepResult(
        ValidationCapability capability,
        ValidationStatus status,
        String summary,
        String details
) {
}
