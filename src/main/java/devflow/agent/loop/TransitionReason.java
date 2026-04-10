package devflow.agent.loop;

public enum TransitionReason {
    STAGE_APPROVED,
    STAGE_CONTINUE,
    HUMAN_REVIEW_REQUIRED,
    STAGE_RETRY,
    STAGE_ROLLBACK,
    REPAIR_ROUTE,
    RUN_COMPLETED,
    RUN_FAILED,
    LOOP_PAUSED
}
