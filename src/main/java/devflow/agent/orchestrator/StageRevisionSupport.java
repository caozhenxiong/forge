package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * 统一处理“阶段被打回后如何回流”的细节。
 *
 * <p>这层专门负责：
 * 1. 人工拒绝后的 review history 落盘
 * 2. revision note / supervisor guidance 的拼装
 * 3. diagnosis 与 repair brief 的触发
 * 4. reroute 到上游阶段时的事件记录
 *
 * <p>这样 StageTransitionSupport 可以更专注于“状态迁移”，
 * 而不是继续兼任修订说明生成器。
 */
public class StageRevisionSupport {

    private final FileRunRepository runRepository;
    private final FileArtifactStore artifactStore;
    private final EventLogStore eventLogStore;
    private final StageFlowPolicy stageFlowPolicy;
    private final WorkflowArtifactRenderer workflowArtifactRenderer;
    private final SupervisorGuidanceRenderer supervisorGuidanceRenderer;
    private final StageRevisionRepairSupport stageRevisionRepairSupport;

    public StageRevisionSupport(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            DiagnosisAgent diagnosisAgent,
            RepairAgent repairAgent,
            StageFlowPolicy stageFlowPolicy,
            WorkflowArtifactRenderer workflowArtifactRenderer
    ) {
        this.runRepository = runRepository;
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
        this.stageFlowPolicy = stageFlowPolicy;
        this.workflowArtifactRenderer = workflowArtifactRenderer;
        this.supervisorGuidanceRenderer = new SupervisorGuidanceRenderer();
        this.stageRevisionRepairSupport = new StageRevisionRepairSupport(
                artifactStore,
                eventLogStore,
                diagnosisAgent,
                repairAgent,
                new StageRevisionNoteBuilder()
        );
    }

    public RunRecord rejectHumanReview(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            String reviewer,
            String reason,
            StageTransitionSupport.StageEntryAction stageEntryAction
    ) {
        StageExecution currentExecution = requireStage(runRecord.stageStates(), stageType);
        DocumentLanguage language = DocumentLanguage.detect(runRecord.goal(), runRecord.constraints());
        artifactStore.appendReviewHistory(
                projectPath,
                runRecord.runId(),
                stageType,
                workflowArtifactRenderer.renderReviewHistoryEntry(
                        stageType,
                        currentExecution.attempt(),
                        "human:" + reviewer,
                        new ReviewResult(ReviewDecision.REJECTED, FixMode.REWORK, "Rejected by " + reviewer, reason),
                        language
                )
        );
        return rerouteForRevision(
                projectPath,
                runRecord,
                stageType,
                ReviewDecision.REJECTED,
                FixMode.REWORK,
                ImplementationPatchTarget.NONE,
                "Rejected by " + reviewer,
                reason,
                "",
                "",
                java.util.List.of(),
                null,
                stageFlowPolicy.rerouteStage(stageType, FixMode.REWORK),
                false,
                false,
                stageEntryAction
        );
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
            StageTransitionSupport.StageEntryAction stageEntryAction
    ) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        StageExecution reviewedExecution = currentExecution.withStatus(StageStatus.NEEDS_REVISION)
                .withReview(decision, summary, changeRequest);
        nextStates.put(stageType, reviewedExecution);

        if (currentExecution.attempt() >= runRecord.config().maxAutoRevisions()) {
            nextStates.put(stageType, reviewedExecution.withStatus(StageStatus.FAILED));
            RunRecord failed = runRecord.withCurrentStage(stageType, RunStatus.FAILED, nextStates, Instant.now());
            eventLogStore.append(projectPath, runRecord.runId(), WorkflowEventMessages.maxAutoRevisionsExceeded(stageType));
            return runRepository.save(failed);
        }

        String revisionNote = stageRevisionRepairSupport.buildRevisionNote(
                projectPath,
                runRecord,
                stageType,
                rerouteStage,
                fixMode,
                implementationPatchTarget,
                summary,
                changeRequest,
                evidence,
                actionItems,
                overrideChanges,
                supervisorDecision,
                forceRepair,
                repeatedIssue
        );

        RunRecord draft = runRecord.withCurrentStage(rerouteStage, RunStatus.IN_PROGRESS, nextStates, Instant.now());
        eventLogStore.append(
                projectPath,
                runRecord.runId(),
                WorkflowEventMessages.reroutedForRevision(stageType, rerouteStage, fixMode)
        );
        return stageEntryAction.enter(draft, rerouteStage, RunStatus.IN_PROGRESS, revisionNote);
    }

    public String mergeActionItems(String actionItems, SupervisorDecision supervisorDecision) {
        // review 的 action items 和 supervisor narrative 必须合并成一份修订说明，
        // 但这里不能再嵌套结构化 directive block，否则后续 parseMerged 会读到截断的 JSON。
        return supervisorGuidanceRenderer.mergeActionItems(actionItems, supervisorDecision);
    }

    /**
     * 统一暴露 supervisor guidance 的渲染结果，避免多个流程类各自重复拼接同一段修订说明。
     */
    public String supervisorGuidance(SupervisorDecision supervisorDecision) {
        return supervisorGuidanceRenderer.renderDirectiveGuidance(supervisorDecision);
    }

    private StageExecution requireStage(Map<StageType, StageExecution> stageStates, StageType stageType) {
        StageExecution stageExecution = stageStates.get(stageType);
        if (stageExecution == null) {
            throw new IllegalArgumentException("Missing stage state for " + stageType);
        }
        return stageExecution;
    }

}
