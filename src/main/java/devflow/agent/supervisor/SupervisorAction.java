package devflow.agent.supervisor;

public enum SupervisorAction {
    ADVANCE_STAGE,
    REQUEST_HUMAN_REVIEW,
    RETRY_STAGE,
    ROUTE_TO_REPAIR,
    ROLLBACK_STAGE,
    COMPLETE_RUN,
    FAIL_RUN
}
