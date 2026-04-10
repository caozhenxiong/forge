package devflow.agent.orchestrator;

/**
 * 统一的流程动作枚举。
 *
 * <p>这层不直接关心某个 agent 的 prompt 或返回格式，
 * 只表达工作流下一步在控制面上的语义动作。
 */
public enum FlowAction {
    ADVANCE_STAGE,
    REQUEST_HUMAN_REVIEW,
    RETRY_STAGE,
    ROUTE_TO_REPAIR,
    ROLLBACK_STAGE,
    COMPLETE_RUN,
    FAIL_RUN
}
