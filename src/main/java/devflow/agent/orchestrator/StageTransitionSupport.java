package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
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

    private final FileRunRepository runRepository;
    private final devflow.agent.artifact.EventLogStore eventLogStore;
    private final StageStatusSupport stageStatusSupport;
    private final StageRevisionSupport stageRevisionSupport;

    public StageTransitionSupport(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            devflow.agent.artifact.EventLogStore eventLogStore,
            devflow.agent.repair.DiagnosisAgent diagnosisAgent,
            devflow.agent.repair.RepairAgent repairAgent,
            StageFlowPolicy stageFlowPolicy,
            WorkflowArtifactRenderer workflowArtifactRenderer
    ) {
        this.runRepository = runRepository;
        this.eventLogStore = eventLogStore;
        this.stageStatusSupport = new StageStatusSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                stageFlowPolicy,
                workflowArtifactRenderer
        );
        this.stageRevisionSupport = new StageRevisionSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                diagnosisAgent,
                repairAgent,
                stageFlowPolicy,
                workflowArtifactRenderer
        );
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
        return stageStatusSupport.approveHumanReview(projectPath, runRecord, stageType, reviewer, stageEntryAction);
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
        return stageRevisionSupport.rejectHumanReview(projectPath, runRecord, stageType, reviewer, reason, stageEntryAction);
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

    public RunRecord blockForHumanReview(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        // human review 是流程阻塞态，不改变当前阶段，只切 run/status 并保留 review 结论。
        return stageStatusSupport.blockForHumanReview(runRecord, stageType, reviewResult);
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
            ReviewDecision decision,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            java.util.List<devflow.agent.executor.FileChange> overrideChanges,
            SupervisorDecision supervisorDecision,
            StageType rerouteStage,
            boolean forceRepair,
            boolean repeatedIssue,
            StageEntryAction stageEntryAction
    ) {
        return stageRevisionSupport.rerouteForRevision(
                projectPath,
                runRecord,
                stageType,
                decision,
                fixMode,
                implementationPatchTarget,
                summary,
                changeRequest,
                evidence,
                actionItems,
                overrideChanges,
                supervisorDecision,
                rerouteStage,
                forceRepair,
                repeatedIssue,
                stageEntryAction
        );
    }

    public RunRecord continueStage(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            java.util.List<devflow.agent.executor.FileChange> overrideChanges,
            ImplementationPatchTarget implementationPatchTarget,
            StageEntryAction stageEntryAction
    ) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        StageExecution continuedExecution = currentExecution.withStatus(StageStatus.NEEDS_REVISION)
                .withReview(null, summary, changeRequest);
        nextStates.put(stageType, continuedExecution);

        if (currentExecution.attempt() >= runRecord.config().maxAutoRevisions()) {
            nextStates.put(stageType, continuedExecution.withStatus(StageStatus.FAILED));
            RunRecord failed = runRecord.withCurrentStage(stageType, RunStatus.FAILED, nextStates, Instant.now());
            eventLogStore.append(projectPath, runRecord.runId(), WorkflowEventMessages.maxAutoRevisionsExceeded(stageType));
            return runRepository.save(failed);
        }

        RunRecord draft = runRecord.withCurrentStage(stageType, RunStatus.IN_PROGRESS, nextStates, Instant.now());
        return stageEntryAction.enter(
                draft,
                stageType,
                RunStatus.IN_PROGRESS,
                continuationNote(summary, changeRequest, evidence, actionItems, overrideChanges, implementationPatchTarget)
        );
    }

    public void markFatalFailure(Path projectPath, UUID runId, StageType stageType, RuntimeException ex) {
        stageStatusSupport.markFatalFailure(projectPath, runId, stageType, ex);
    }

    public String mergeActionItems(String actionItems, SupervisorDecision supervisorDecision) {
        return stageRevisionSupport.mergeActionItems(actionItems, supervisorDecision);
    }

    private String continuationNote(
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            java.util.List<devflow.agent.executor.FileChange> overrideChanges,
            ImplementationPatchTarget implementationPatchTarget
    ) {
        return ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        implementationPatchTarget == null ? ImplementationPatchTarget.NONE.name() : implementationPatchTarget.name(),
                        overrideChanges == null ? java.util.List.of() : overrideChanges.stream().map(change -> new devflow.agent.protocol.FileChangePayload(
                                change.path(),
                                change.action() == null ? null : change.action().name(),
                                change.reason() == null ? "" : change.reason(),
                                change.effectiveEditScope().name(),
                                change.runtimeOwnership() == null ? null : change.runtimeOwnership().name(),
                                change.hostHtmlPatchRequired()
                        )).toList(),
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        summary,
                        changeRequest,
                        evidence,
                        actionItems,
                        null,
                        "继续当前 implementation 阶段，收敛未完成的问题。",
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null,
                        null,
                        null
                ),
                summary,
                changeRequest,
                evidence,
                actionItems
        );
    }

    @FunctionalInterface
    public interface StageEntryAction {
        RunRecord enter(RunRecord runRecord, StageType stageType, RunStatus runStatus, String note);
    }

    private StageExecution requireStage(Map<StageType, StageExecution> stageStates, StageType stageType) {
        StageExecution stageExecution = stageStates.get(stageType);
        if (stageExecution == null) {
            throw new IllegalArgumentException("Missing stage state for " + stageType);
        }
        return stageExecution;
    }
}
