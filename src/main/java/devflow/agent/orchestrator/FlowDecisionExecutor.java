package devflow.agent.orchestrator;

import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;

/**
 * 负责把 FlowController 的结构化决策真正落实成阶段动作。
 *
 * <p>职责边界：
 * 1. 不判断流程是否应该前进
 * 2. 不重新解释 review / supervisor 语义
 * 3. 只把既定的 FlowAction 映射到对应的阶段迁移动作
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
        FlowAction action = flowDecision.action();
        if (action == FlowAction.ADVANCE_STAGE) {
            return stageTransitionSupport.onStageApproved(
                    projectPath,
                    runRecord,
                    stageType,
                    reviewResult,
                    supervisorDecision,
                    stageEntryExecutor::enterStage
            );
        }
        if (action == FlowAction.REQUEST_HUMAN_REVIEW) {
            return stageTransitionSupport.blockForHumanReview(runRecord, stageType, reviewResult);
        }
        if (action == FlowAction.COMPLETE_RUN) {
            return stageTransitionSupport.completeRun(runRecord, stageType, reviewResult);
        }
        if (action == FlowAction.RETRY_STAGE || action == FlowAction.ROLLBACK_STAGE) {
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
        if (action == FlowAction.ROUTE_TO_REPAIR) {
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
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            devflow.agent.review.ImplementationPatchTarget implementationPatchTarget
    ) {
            return stageTransitionSupport.continueStage(
                projectPath,
                runRecord,
                stageType,
                summary,
                changeRequest,
                evidence,
                actionItems,
                implementationPatchTarget,
                stageEntryExecutor::enterStage
        );
    }
}
