package devflow.agent.loop;

/**
 * 统一的 agent 内部 turn 状态机。
 * 这层描述的是单个角色在“一轮内部工作”中的推进，而不是 workflow 的外层阶段状态。
 */
public enum AgentTurnState {
    IDLE,
    PREPARE_CONTEXT,
    SELECT_NEXT_UNIT,
    EXECUTE_STEP,
    OBSERVE_RESULT,
    EVALUATE_RESULT,
    REQUEST_CONTINUATION,
    REQUEST_HANDOFF,
    COMPLETE,
    FAILED
}
