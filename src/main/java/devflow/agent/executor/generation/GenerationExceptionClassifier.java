package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

@FunctionalInterface
public interface GenerationExceptionClassifier {

    GenerationFailureType classify(Exception exception);
}
