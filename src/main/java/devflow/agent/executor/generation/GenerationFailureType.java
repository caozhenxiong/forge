package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public enum GenerationFailureType {
    MODEL_INVOCATION_FAILED,
    ATTEMPT_TIMEOUT,
    OUTPUT_TRUNCATED,
    MODEL_OUTPUT_INVALID,
    SNAPSHOT_STALE,
    TARGET_SCOPE_VIOLATION,
    TARGET_NOT_FOUND,
    TARGET_NOT_UNIQUE,
    SYNTAX_INVALID,
    NO_MATERIAL_CHANGE,
    VALIDATION_FAILED
}
