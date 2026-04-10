package devflow.agent.executor;

public record ArchitectIntegrationCheckResult(
        boolean passed,
        ArchitectIntegrationFailureReason failureReason,
        String details
) {

    public static ArchitectIntegrationCheckResult success() {
        return new ArchitectIntegrationCheckResult(true, null, "");
    }

    public static ArchitectIntegrationCheckResult failure(ArchitectIntegrationFailureReason failureReason, String details) {
        return new ArchitectIntegrationCheckResult(false, failureReason, details);
    }
}
