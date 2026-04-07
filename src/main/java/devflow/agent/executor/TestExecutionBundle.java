package devflow.agent.executor;

public record TestExecutionBundle(
        String testCasesMarkdown,
        String runtimeSnapshotMarkdown,
        String executionMarkdown,
        String reportMarkdown
) {
}
