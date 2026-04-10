package devflow.agent.supervisor;

public enum GenerationRecoveryAction {
    RETRY_SUBTASK,
    ROUTE_TO_REPAIR,
    FAIL_SUBTASK
}
