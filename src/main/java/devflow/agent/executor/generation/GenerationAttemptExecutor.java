package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

@FunctionalInterface
public interface GenerationAttemptExecutor<T> {

    GenerationAttemptResult<T> execute(int attempt, String retryFeedback) throws Exception;
}
