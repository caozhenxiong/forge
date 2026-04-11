package devflow.agent.executor;

/**
 * TEST 阶段失败的稳定归因。
 */
public enum ExperienceFailureKind {
    NONE,
    RUNTIME_PROBE_INVALID,
    TEST_CASE_INCOMPLETE,
    OBSERVATION_CONTRACT_INVALID,
    IMPLEMENTATION_CAPABILITY_GAP,
    UNKNOWN
}
