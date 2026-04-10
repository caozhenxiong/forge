package devflow.agent.executor;

@FunctionalInterface
public interface GenerationExceptionClassifier {

    GenerationFailureType classify(Exception exception);
}
