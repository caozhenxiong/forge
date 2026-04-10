package devflow.agent.loop;

/**
 * 描述单个 agent 在本轮 turn 中的瞬时状态。
 * 这份快照后续可以被接到 tracing、事件流和四层上下文上，而不是继续让内部过程保持黑盒。
 */
public record AgentTurnSnapshot(
        AgentTurnState state,
        int stepIndex,
        String activeUnit,
        String summary
) {

    public static AgentTurnSnapshot start() {
        return new AgentTurnSnapshot(AgentTurnState.IDLE, 0, null, null);
    }

    public AgentTurnSnapshot next(AgentTurnState nextState, String nextUnit, String nextSummary) {
        return new AgentTurnSnapshot(nextState, stepIndex + 1, nextUnit, nextSummary);
    }

    public boolean terminal() {
        return state == AgentTurnState.COMPLETE || state == AgentTurnState.FAILED;
    }
}
