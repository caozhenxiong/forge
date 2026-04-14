package devflow.agent.domain;

/**
 * 工作流控制面的唯一动作枚举。
 *
 * <p>supervisor、flow controller 和执行器都只表达同一套动作语义，
 * 不再为同一职责保留多份并行枚举。
 */
public enum WorkflowAction {
    ADVANCE_STAGE,
    REQUEST_HUMAN_REVIEW,
    RETRY_STAGE,
    ROUTE_TO_REPAIR,
    ROLLBACK_STAGE,
    COMPLETE_RUN,
    FAIL_RUN
}
