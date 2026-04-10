package devflow.agent.orchestrator;

import devflow.agent.loop.TransitionDecision;

/**
 * FlowController 的输出。
 *
 * <p>它把“当前应该做什么”和“为什么这样流转”收敛成一个对象，
 * 避免 DefaultWorkflowEngine 同时维护两套平行判断逻辑。
 */
public record FlowDecision(
        FlowAction action,
        StageType targetStage,
        TransitionDecision transitionDecision
) {
}
