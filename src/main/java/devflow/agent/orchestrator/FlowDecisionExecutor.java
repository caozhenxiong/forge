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
        ReviewResult effectiveReviewResult = effectiveReviewResult(reviewResult, flowDecision);
        WorkflowAction action = flowDecision.action();
        if (action == WorkflowAction.ADVANCE_STAGE) {
            return stageTransitionSupport.onStageApproved(
                    projectPath,
                    runRecord,
                    stageType,
                    effectiveReviewResult,
                    supervisorDecision,
                    stageEntryExecutor::enterStage
            );
        }
        if (action == WorkflowAction.REQUEST_HUMAN_REVIEW) {
            return stageTransitionSupport.blockForHumanReview(runRecord, stageType, effectiveReviewResult);
        }
        if (action == WorkflowAction.COMPLETE_RUN) {
            return stageTransitionSupport.completeRun(runRecord, stageType, effectiveReviewResult);
        }
        if (action == WorkflowAction.RETRY_STAGE
                || action == WorkflowAction.ROLLBACK_STAGE
                || action == WorkflowAction.ROUTE_TO_REPAIR) {
            return rerouteForRevision(
                projectPath,
                runRecord,
                stageType,
                effectiveReviewResult,
                repeatedIssue,
                supervisorDecision,
                flowDecision,
                action == WorkflowAction.ROUTE_TO_REPAIR
            );
        }
        return stageTransitionSupport.failRun(runRecord, stageType, effectiveReviewResult);
    }

    private RunRecord rerouteForRevision(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision supervisorDecision,
            FlowDecision flowDecision,
            boolean forceRepair
    ) {
        RevisionRoutingPlan revisionRoutingPlan = flowDecision.revisionRoutingPlan();
        return stageTransitionSupport.rerouteForRevision(
                projectPath,
                runRecord,
                stageType,
                new RevisionContext(
                        reviewResult.decision(),
                        effectiveFixMode(supervisorDecision, revisionRoutingPlan),
                        effectivePatchTarget(reviewResult, revisionRoutingPlan),
                        reviewResult.summary(),
                        reviewResult.changeRequest(),
                        reviewResult.evidence(),
                        stageTransitionSupport.mergeActionItems(reviewResult.actionItems(), supervisorDecision),
                        effectiveOverrideChanges(reviewResult, revisionRoutingPlan),
                        supervisorDecision,
                        flowDecision.targetStage(),
                        forceRepair,
                        repeatedIssue
                ),
                stageEntryExecutor::enterStage
        );
    }

    private devflow.agent.review.FixMode effectiveFixMode(
            SupervisorDecision supervisorDecision,
            RevisionRoutingPlan revisionRoutingPlan
    ) {
        if (revisionRoutingPlan != null && revisionRoutingPlan.active()) {
            return revisionRoutingPlan.fixMode();
        }
        return supervisorDecision.mode();
    }

    private devflow.agent.review.ImplementationPatchTarget effectivePatchTarget(
            ReviewResult reviewResult,
            RevisionRoutingPlan revisionRoutingPlan
    ) {
        if (revisionRoutingPlan != null && revisionRoutingPlan.active()) {
            return revisionRoutingPlan.implementationPatchTarget();
        }
        return reviewResult.implementationPatchTarget();
    }

    private java.util.List<devflow.agent.executor.FileChange> effectiveOverrideChanges(
            ReviewResult reviewResult,
            RevisionRoutingPlan revisionRoutingPlan
    ) {
        if (revisionRoutingPlan != null && revisionRoutingPlan.active()) {
            return revisionRoutingPlan.overrideChanges();
        }
        return reviewResult.overrideChanges();
    }

    private ReviewResult effectiveReviewResult(ReviewResult reviewResult, FlowDecision flowDecision) {
        if (flowDecision != null && flowDecision.reviewResultOverride() != null) {
            return flowDecision.reviewResultOverride();
        }
        return reviewResult;
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
