package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * TEST 阶段失败的稳定归因。
 */
public enum ExperienceFailureKind {
    NONE,
    RUNTIME_PROBE_INVALID,
    TEST_PLAN_DEFECT,
    TEST_CASE_INCOMPLETE,
    OBSERVATION_CONTRACT_INVALID,
    IMPLEMENTATION_CAPABILITY_GAP,
    UNKNOWN;

    public boolean requiresImplementationReverification() {
        return this == OBSERVATION_CONTRACT_INVALID || this == IMPLEMENTATION_CAPABILITY_GAP;
    }
}
