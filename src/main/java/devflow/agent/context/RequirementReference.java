package devflow.agent.context;

public record RequirementReference(
        String id,
        String category,
        String text,
        boolean planningRequired
) {
}
