package devflow.agent.executor;

@FunctionalInterface
public interface GenerationFailureFactory {

    GenerationFailureException create(GenerationFailureType failureType, String evidence);
}
