package devflow.agent.executor;

public record TestCaseResult(
        String id,
        String title,
        boolean passed,
        boolean required,
        String details
) {
}
