package devflow.agent.orchestrator;

import devflow.agent.domain.WorkflowAction;
import devflow.agent.domain.StageType;

import devflow.agent.loop.TransitionDecision;
import devflow.agent.review.ReviewResult;

/**
 * FlowController 的输出。
 *
 * <p>它把“当前应该做什么”和“为什么这样流转”收敛成一个对象，
 * 避免 DefaultWorkflowEngine 同时维护两套平行判断逻辑。
 */
public record FlowDecision(
        WorkflowAction action,
        StageType targetStage,
        TransitionDecision transitionDecision,
        RevisionRoutingPlan revisionRoutingPlan,
        ReviewResult reviewResultOverride
) {
    public FlowDecision(WorkflowAction action, StageType targetStage, TransitionDecision transitionDecision) {
        this(action, targetStage, transitionDecision, RevisionRoutingPlan.none(), null);
    }

    public FlowDecision(
            WorkflowAction action,
            StageType targetStage,
            TransitionDecision transitionDecision,
            RevisionRoutingPlan revisionRoutingPlan
    ) {
        this(action, targetStage, transitionDecision, revisionRoutingPlan, null);
    }
}
