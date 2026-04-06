package devflow.agent.executor;

public record TestExecutionBundle(
        String testCasesMarkdown,
        String executionMarkdown,
        String reportMarkdown
) {
}
