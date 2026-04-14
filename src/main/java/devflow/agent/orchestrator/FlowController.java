package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.loop.TransitionDecision;
import devflow.agent.loop.TransitionReason;
import devflow.agent.review.ReviewResult;
import devflow.agent.domain.WorkflowAction;
import devflow.agent.supervisor.SupervisorDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(FlowController.class);
    private static final String TOOL_SUMMARY_SEPARATOR = " | ";

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
        WorkflowAction action = supervisorDecision.action();
        StageType targetStage = supervisorDecision.targetStage();
        String transitionSummary = reviewResult.summary() == null ? "" : reviewResult.summary();
        if (shouldRetryCurrentStage(stageType, action, toolSummary)) {
            action = WorkflowAction.RETRY_STAGE;
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
        if (currentStage == null) {
            log.warn(
                    "shouldContinue: currentStage not found in stageStates, runId={} stage={}",
                    runRecord.runId(),
                    runRecord.currentStage()
            );
            return false;
        }
        return runRecord.status() == RunStatus.IN_PROGRESS
                && currentStage.status() == StageStatus.RUNNING;
    }

    private TransitionReason mapReason(WorkflowAction action, StageType stageType) {
        return switch (action) {
            case ADVANCE_STAGE -> StageType.TEST == stageType ? TransitionReason.RUN_COMPLETED : TransitionReason.STAGE_APPROVED;
            case REQUEST_HUMAN_REVIEW -> TransitionReason.HUMAN_REVIEW_REQUIRED;
            case RETRY_STAGE -> TransitionReason.STAGE_RETRY;
            case ROUTE_TO_REPAIR -> TransitionReason.REPAIR_ROUTE;
            case ROLLBACK_STAGE -> TransitionReason.STAGE_ROLLBACK;
            case COMPLETE_RUN -> TransitionReason.RUN_COMPLETED;
            case FAIL_RUN -> TransitionReason.RUN_FAILED;
        };
    }

    private boolean shouldRetryCurrentStage(StageType stageType, WorkflowAction action, StageToolResultSummary toolSummary) {
        if (toolSummary == null || !toolSummary.blockingFailure()) {
            return false;
        }
        return action == WorkflowAction.ADVANCE_STAGE || action == WorkflowAction.COMPLETE_RUN;
    }

    private String appendToolSummary(String reviewSummary, StageToolResultSummary toolSummary) {
        if (toolSummary == null || toolSummary.summary().isBlank()) {
            return reviewSummary == null ? "" : reviewSummary;
        }
        if (reviewSummary == null || reviewSummary.isBlank()) {
            return toolSummary.summary();
        }
        return reviewSummary + TOOL_SUMMARY_SEPARATOR + toolSummary.summary();
    }
}
