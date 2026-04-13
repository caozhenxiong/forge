package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public enum ModelRole {
    ANALYSIS,
    PRD,
    DESIGN,
    IMPLEMENTATION,
    CODE_REVIEW,
    TEST,
    TEST_CASE_DESIGN,
    VALIDATION_STRATEGY,
    DIAGNOSIS,
    REPAIR,
    SUPERVISOR
}
