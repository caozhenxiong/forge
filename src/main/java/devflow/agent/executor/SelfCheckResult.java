package devflow.agent.executor;

public record SelfCheckResult(
        boolean passed,
        String summary,
        String details
) {
}
