package devflow.agent.orchestrator;

import devflow.agent.loop.TransitionDecision;
import devflow.agent.loop.TransitionReason;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorAction;
import devflow.agent.supervisor.SupervisorDecision;
import org.springframework.stereotype.Component;

/**
 * 统一的流程控制器。
 *
 * <p>这层只负责把结构化输入映射成稳定的流程动作，不直接生成代码、
 * 不直接审查产物，也不处理持久化。这样可以把“流程怎么走”的决定权
 * 从多个类里收敛到一个地方。
 */
@Component
public class FlowController {

    /**
     * 将 supervisor 的流程建议转换成统一的流程动作和流转说明。
     */
    public FlowDecision decide(
            StageType stageType,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision supervisorDecision,
            StageToolResultSummary toolSummary
    ) {
        FlowAction action = mapAction(supervisorDecision.action());
        StageType targetStage = supervisorDecision.targetStage();
        String transitionSummary = reviewResult.summary() == null ? "" : reviewResult.summary();
        if (shouldRetryCurrentStage(stageType, action, toolSummary)) {
            action = FlowAction.RETRY_STAGE;
            targetStage = stageType;
            transitionSummary = appendToolSummary(transitionSummary, toolSummary);
        }
        TransitionReason reason = mapReason(action, stageType);
        TransitionDecision transitionDecision = new TransitionDecision(
                reason,
                stageType,
                targetStage,
                repeatedIssue,
                transitionSummary,
                supervisorDecision
        );
        return new FlowDecision(action, targetStage, transitionDecision);
    }

    /**
     * 运行一个流程动作后，统一判断 agent loop 是否还应该继续前进。
     */
    public boolean shouldContinue(RunRecord runRecord) {
        StageExecution currentStage = runRecord.stageStates().get(runRecord.currentStage());
        return runRecord.status() == RunStatus.IN_PROGRESS
                && currentStage != null
                && currentStage.status() == StageStatus.RUNNING;
    }

    private FlowAction mapAction(SupervisorAction action) {
        if (action == SupervisorAction.ADVANCE_STAGE) {
            return FlowAction.ADVANCE_STAGE;
        }
        if (action == SupervisorAction.REQUEST_HUMAN_REVIEW) {
            return FlowAction.REQUEST_HUMAN_REVIEW;
        }
        if (action == SupervisorAction.RETRY_STAGE) {
            return FlowAction.RETRY_STAGE;
        }
        if (action == SupervisorAction.ROUTE_TO_REPAIR) {
            return FlowAction.ROUTE_TO_REPAIR;
        }
        if (action == SupervisorAction.ROLLBACK_STAGE) {
            return FlowAction.ROLLBACK_STAGE;
        }
        if (action == SupervisorAction.COMPLETE_RUN) {
            return FlowAction.COMPLETE_RUN;
        }
        if (action == SupervisorAction.FAIL_RUN) {
            return FlowAction.FAIL_RUN;
        }
        throw new IllegalArgumentException("Unsupported supervisor action: " + action);
    }

    private TransitionReason mapReason(FlowAction action, StageType stageType) {
        if (action == FlowAction.ADVANCE_STAGE) {
            return StageType.TEST == stageType ? TransitionReason.RUN_COMPLETED : TransitionReason.STAGE_APPROVED;
        }
        if (action == FlowAction.REQUEST_HUMAN_REVIEW) {
            return TransitionReason.HUMAN_REVIEW_REQUIRED;
        }
        if (action == FlowAction.RETRY_STAGE) {
            return TransitionReason.STAGE_RETRY;
        }
        if (action == FlowAction.ROUTE_TO_REPAIR) {
            return TransitionReason.REPAIR_ROUTE;
        }
        if (action == FlowAction.ROLLBACK_STAGE) {
            return TransitionReason.STAGE_ROLLBACK;
        }
        if (action == FlowAction.COMPLETE_RUN) {
            return TransitionReason.RUN_COMPLETED;
        }
        if (action == FlowAction.FAIL_RUN) {
            return TransitionReason.RUN_FAILED;
        }
        throw new IllegalArgumentException("Unsupported flow action: " + action);
    }

    private boolean shouldRetryCurrentStage(StageType stageType, FlowAction action, StageToolResultSummary toolSummary) {
        if (toolSummary == null || !toolSummary.blockingFailure()) {
            return false;
        }
        return action == FlowAction.ADVANCE_STAGE || action == FlowAction.COMPLETE_RUN;
    }

    private String appendToolSummary(String reviewSummary, StageToolResultSummary toolSummary) {
        if (toolSummary == null || toolSummary.summary().isBlank()) {
            return reviewSummary == null ? "" : reviewSummary;
        }
        if (reviewSummary == null || reviewSummary.isBlank()) {
            return toolSummary.summary();
        }
        return reviewSummary + " | " + toolSummary.summary();
    }
}
