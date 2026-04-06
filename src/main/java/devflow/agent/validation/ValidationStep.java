package devflow.agent.validation;

public record ValidationStep(
        ValidationCapability capability,
        String reason,
        boolean required
) {
}
