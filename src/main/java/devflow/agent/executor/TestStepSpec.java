package devflow.agent.executor;

public record TestStepSpec(
        String action,
        String selector,
        String key,
        Integer count,
        Integer ms,
        String text,
        Boolean optional
) {
}
