package devflow.agent.orchestrator;

import devflow.agent.domain.HumanReviewIntent;
import devflow.agent.domain.HumanReviewResolutionContext;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 负责把 FlowController 的动作真正落实成 run/stage 的状态变化。
 * 这里不做流程判断，只负责：
 * 1. 基于当前状态生成下一份 RunRecord
 * 2. 写入必要的事件和辅助产物
 * 3. 在需要跳转阶段时调用统一的 enterStage 回调
 */
public class StageTransitionSupport {

    private final StageStatusSupport stageStatusSupport;
    private final StageRevisionSupport stageRevisionSupport;
    private final StageContinuationNoteBuilder stageContinuationNoteBuilder;

    public StageTransitionSupport(
            StageStatusSupport stageStatusSupport,
            StageRevisionSupport stageRevisionSupport,
            StageContinuationNoteBuilder stageContinuationNoteBuilder
    ) {
        this.stageStatusSupport = stageStatusSupport;
        this.stageRevisionSupport = stageRevisionSupport;
        this.stageContinuationNoteBuilder = stageContinuationNoteBuilder;
    }

    public RunRecord approveHumanReview(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            String reviewer,
            StageEntryAction stageEntryAction
    ) {
        if (runRecord.currentStage() != stageType) {
            throw new IllegalStateException("Stage " + stageType + " is not current stage " + runRecord.currentStage());
        }
        HumanReviewResolutionContext context = requireHumanReviewContext(runRecord, stageType);
        if (context.intent() == HumanReviewIntent.APPROVE_STAGE_GATE) {
            return stageStatusSupport.approveHumanReview(projectPath, runRecord, stageType, reviewer, stageEntryAction);
        }
        if (context.terminal()) {
            throw new TerminalHumanApprovalRejectedException(runRecord, stageType, context, reviewer);
        }
        return stageRevisionSupport.rerouteForRevision(
                projectPath,
                runRecord.withHumanReviewResolutionContext(null),
                stageType,
                new RevisionContext(
                        currentReviewDecision(runRecord, stageType),
                        context.fixMode(),
                        context.implementationPatchTarget(),
                        context.summary(),
                        context.changeRequest(),
                        context.evidence(),
                        context.actionItems(),
                        context.overrideChanges(),
                        null,
                        context.targetStage() == null ? stageType : context.targetStage(),
                        true,
                        false
                ),
                stageEntryAction
        );
    }

    public RunRecord rejectHumanReview(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            String reviewer,
            String reason,
            StageEntryAction stageEntryAction
    ) {
        if (runRecord.currentStage() != stageType) {
            throw new IllegalStateException("Stage " + stageType + " is not current stage " + runRecord.currentStage());
        }
        HumanReviewResolutionContext context = requireHumanReviewContext(runRecord, stageType);
        if (context.intent() == HumanReviewIntent.CONFIRM_REPAIR_ROUTE) {
            return stageStatusSupport.rejectRepairRouteTerminal(projectPath, runRecord, stageType, reviewer, reason);
        }
        return stageRevisionSupport.rejectHumanReview(
                projectPath,
                runRecord.withHumanReviewResolutionContext(null),
                stageType,
                reviewer,
                reason,
                stageEntryAction
        );
    }

    public RunRecord onStageApproved(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult,
            SupervisorDecision supervisorDecision,
            StageEntryAction stageEntryAction
    ) {
        // 自动批准只负责“落状态 + 跳下一阶段”，不再让 workflow engine 自己拼下一阶段说明。
        StageType nextStage = supervisorDecision.targetStage();
        return stageStatusSupport.onStageApproved(
                runRecord,
                stageType,
                reviewResult,
                nextStage,
                "由 " + stageType + " 阶段自动批准后进入当前阶段。\n\n" + stageRevisionSupport.supervisorGuidance(supervisorDecision),
                stageEntryAction
        );
    }

    public RunRecord blockForHumanReview(
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult,
            HumanReviewResolutionContext context
    ) {
        // human review 是流程阻塞态，不改变当前阶段，只切 run/status 并保留 review 结论。
        return stageStatusSupport.blockForHumanReview(runRecord, stageType, reviewResult, context);
    }

    public RunRecord completeRun(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        // 完成 run 前仍然要把当前阶段显式标成 APPROVED，避免 run 完成但 stage 悬空。
        return stageStatusSupport.completeRun(runRecord, stageType, reviewResult);
    }

    public RunRecord failRun(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        // 失败 run 和阶段失败需要一起落盘，保证 run.json 能作为唯一事实源。
        return stageStatusSupport.failRun(runRecord, stageType, reviewResult);
    }

    /**
     * 当前阶段被打回时，统一在这里决定是直接附带修订说明回流，还是先做 diagnosis 再生成 repair note。
     */
    public RunRecord rerouteForRevision(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            RevisionContext revisionContext,
            StageEntryAction stageEntryAction
    ) {
        return stageRevisionSupport.rerouteForRevision(
                projectPath,
                runRecord,
                stageType,
                revisionContext,
                stageEntryAction
        );
    }

    public RunRecord continueStage(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            StageContinuationContext continuationContext,
            StageEntryAction stageEntryAction
    ) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = StageStatusSupport.requireStage(nextStates, stageType);
        StageExecution continuedExecution = currentExecution.withStatus(StageStatus.NEEDS_REVISION)
                .withReview(null, continuationContext.summary(), continuationContext.changeRequest());
        nextStates.put(stageType, continuedExecution);
        java.util.Optional<RunRecord> failed =
                stageStatusSupport.applyMaxRevisionGuard(projectPath, runRecord, stageType, currentExecution, nextStates);
        if (failed.isPresent()) {
            return failed.get();
        }

        RunRecord draft = runRecord.withCurrentStage(stageType, RunStatus.IN_PROGRESS, nextStates, Instant.now());
        return stageEntryAction.enter(
                draft,
                stageType,
                RunStatus.IN_PROGRESS,
                stageContinuationNoteBuilder.build(continuationContext)
        );
    }

    public void markFatalFailure(Path projectPath, UUID runId, StageType stageType, RuntimeException ex) {
        stageStatusSupport.markFatalFailure(projectPath, runId, stageType, ex);
    }

    public String mergeActionItems(String actionItems, SupervisorDecision supervisorDecision) {
        return stageRevisionSupport.mergeActionItems(actionItems, supervisorDecision);
    }

    private HumanReviewResolutionContext requireHumanReviewContext(RunRecord runRecord, StageType stageType) {
        HumanReviewResolutionContext context = runRecord.humanReviewResolutionContext();
        if (context == null) {
            throw new IllegalStateException("Missing human review context for stage " + stageType);
        }
        return context;
    }

    private devflow.agent.review.ReviewDecision currentReviewDecision(RunRecord runRecord, StageType stageType) {
        StageExecution execution = StageStatusSupport.requireStage(runRecord.stageStates(), stageType);
        return execution.reviewDecision() == null
                ? devflow.agent.review.ReviewDecision.REVISION_REQUIRED
                : execution.reviewDecision();
    }
    @FunctionalInterface
    public interface StageEntryAction {
        RunRecord enter(RunRecord runRecord, StageType stageType, RunStatus runStatus, String note);
    }
}
