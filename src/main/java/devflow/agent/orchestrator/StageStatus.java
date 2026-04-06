package devflow.agent.orchestrator;

public enum StageStatus {
    PENDING,
    RUNNING,
    NEEDS_REVISION,
    AWAITING_HUMAN_REVIEW,
    APPROVED,
    FAILED,
    SKIPPED
}

