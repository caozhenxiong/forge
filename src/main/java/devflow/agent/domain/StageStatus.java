package devflow.agent.domain;

public enum StageStatus {
    PENDING,
    RUNNING,
    NEEDS_REVISION,
    AWAITING_HUMAN_REVIEW,
    APPROVED,
    FAILED,
    SKIPPED
}

