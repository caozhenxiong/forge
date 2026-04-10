package devflow.agent.loop;

/**
 * 单步 turn 执行结果。
 * continueLoop 由内层状态机决定，外层调用方不需要重复猜“这一轮还要不要继续走”。
 */
public record AgentTurnStepResult(
        AgentTurnSnapshot snapshot,
        boolean continueLoop
) {

    public static AgentTurnStepResult advance(AgentTurnSnapshot snapshot) {
        return new AgentTurnStepResult(snapshot, true);
    }

    public static AgentTurnStepResult stop(AgentTurnSnapshot snapshot) {
        return new AgentTurnStepResult(snapshot, false);
    }
}
