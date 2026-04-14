package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.domain.WorkflowAction;

import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;

/**
 * 负责把 FlowController 的结构化决策真正落实成阶段动作。
 *
 * <p>职责边界：
 * 1. 不判断流程是否应该前进
 * 2. 不重新解释 review / supervisor 语义
 * 3. 只把既定的 WorkflowAction 映射到对应的阶段迁移动作
 */
public class FlowDecisionExecutor {

    private final StageTransitionSupport stageTransitionSupport;
    private final StageEntryExecutor stageEntryExecutor;

    public FlowDecisionExecutor(
            StageTransitionSupport stageTransitionSupport,
            StageEntryExecutor stageEntryExecutor
    ) {
        this.stageTransitionSupport = stageTransitionSupport;
        this.stageEntryExecutor = stageEntryExecutor;
    }

    public RunRecord apply(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision supervisorDecision,
            FlowDecision flowDecision
    ) {
        WorkflowAction action = flowDecision.action();
        if (action == WorkflowAction.ADVANCE_STAGE) {
            return stageTransitionSupport.onStageApproved(
                    projectPath,
                    runRecord,
                    stageType,
                    reviewResult,
                    supervisorDecision,
                    stageEntryExecutor::enterStage
            );
        }
        if (action == WorkflowAction.REQUEST_HUMAN_REVIEW) {
            return stageTransitionSupport.blockForHumanReview(runRecord, stageType, reviewResult);
        }
        if (action == WorkflowAction.COMPLETE_RUN) {
            return stageTransitionSupport.completeRun(runRecord, stageType, reviewResult);
        }
        if (action == WorkflowAction.RETRY_STAGE || action == WorkflowAction.ROLLBACK_STAGE) {
            return stageTransitionSupport.rerouteForRevision(
                    projectPath,
                    runRecord,
                    stageType,
                    reviewResult.decision(),
                    supervisorDecision.mode(),
                    reviewResult.implementationPatchTarget(),
                    reviewResult.summary(),
                    reviewResult.changeRequest(),
                    reviewResult.evidence(),
                    stageTransitionSupport.mergeActionItems(reviewResult.actionItems(), supervisorDecision),
                    reviewResult.overrideChanges(),
                    supervisorDecision,
                    flowDecision.targetStage(),
                    false,
                    repeatedIssue,
                    stageEntryExecutor::enterStage
            );
        }
        if (action == WorkflowAction.ROUTE_TO_REPAIR) {
            return stageTransitionSupport.rerouteForRevision(
                    projectPath,
                    runRecord,
                    stageType,
                    reviewResult.decision(),
                    supervisorDecision.mode(),
                    reviewResult.implementationPatchTarget(),
                    reviewResult.summary(),
                    reviewResult.changeRequest(),
                    reviewResult.evidence(),
                    stageTransitionSupport.mergeActionItems(reviewResult.actionItems(), supervisorDecision),
                    reviewResult.overrideChanges(),
                    supervisorDecision,
                    flowDecision.targetStage(),
                    true,
                    repeatedIssue,
                    stageEntryExecutor::enterStage
            );
        }
        return stageTransitionSupport.failRun(runRecord, stageType, reviewResult);
    }

    public RunRecord continueStage(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            StageContinuationContext continuationContext
    ) {
        return stageTransitionSupport.continueStage(
                projectPath,
                runRecord,
                stageType,
                continuationContext,
                stageEntryExecutor::enterStage
        );
    }

    public RunRecord blockForHumanReview(
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult
    ) {
        return stageTransitionSupport.blockForHumanReview(runRecord, stageType, reviewResult);
    }
}
