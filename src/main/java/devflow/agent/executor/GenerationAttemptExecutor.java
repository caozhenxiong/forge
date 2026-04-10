package devflow.agent.executor;

@FunctionalInterface
public interface GenerationAttemptExecutor<T> {

    GenerationAttemptResult<T> execute(int attempt, String retryFeedback) throws Exception;
}
