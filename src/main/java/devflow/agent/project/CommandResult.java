package devflow.agent.project;

public record CommandResult(
        int exitCode,
        String stdout,
        String stderr
) {
}
