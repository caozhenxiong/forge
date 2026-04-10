package devflow.agent.loop;

import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 通用的 agent 内部 turn loop。
 *
 * <p>第一版只负责：
 * 1. 推动内部状态机从一个快照走到下一个快照；
 * 2. 在 terminal 或显式 stop 时收束；
 * 3. 避免每个角色都再手写一份 while/switch 驱动逻辑。
 *
 * <p>这层暂时不负责 tracing、持久化和事件写盘；后续 Phase 6 会逐步把这些能力接进来。
 */
@Component
public class AgentTurnLoop {

    public AgentTurnSnapshot runUntilSettled(
            AgentTurnSnapshot initial,
            Function<AgentTurnSnapshot, AgentTurnStepResult> stepFunction
    ) {
        AgentTurnSnapshot current = initial == null ? AgentTurnSnapshot.start() : initial;
        while (true) {
            if (current.terminal()) {
                return current;
            }
            AgentTurnStepResult stepResult = Objects.requireNonNull(stepFunction.apply(current), "stepFunction result");
            AgentTurnSnapshot next = Objects.requireNonNull(stepResult.snapshot(), "step snapshot");
            if (!stepResult.continueLoop()) {
                return next;
            }
            current = next;
        }
    }
}
