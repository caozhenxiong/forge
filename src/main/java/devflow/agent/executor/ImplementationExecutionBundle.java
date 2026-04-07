package devflow.agent.executor;

public record ImplementationExecutionBundle(
        String implementationMarkdown,
        String backlogMarkdown,
        String repairAlignmentMarkdown
) {
}
