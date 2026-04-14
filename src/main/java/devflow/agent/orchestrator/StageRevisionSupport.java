package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
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

    private final FileArtifactStore artifactStore;
    private final EventLogStore eventLogStore;
    private final StageFlowPolicy stageFlowPolicy;
    private final WorkflowArtifactRenderer workflowArtifactRenderer;
    private final SupervisorGuidanceRenderer supervisorGuidanceRenderer;
    private final StageRevisionRepairSupport stageRevisionRepairSupport;
    private final StageStatusSupport stageStatusSupport;
    private final LanguagePolicy languagePolicy;

    public StageRevisionSupport(
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageFlowPolicy stageFlowPolicy,
            WorkflowArtifactRenderer workflowArtifactRenderer,
            SupervisorGuidanceRenderer supervisorGuidanceRenderer,
            StageRevisionRepairSupport stageRevisionRepairSupport,
            StageStatusSupport stageStatusSupport,
            LanguagePolicy languagePolicy
    ) {
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
        this.stageFlowPolicy = stageFlowPolicy;
        this.workflowArtifactRenderer = workflowArtifactRenderer;
        this.supervisorGuidanceRenderer = supervisorGuidanceRenderer;
        this.languagePolicy = languagePolicy;
        this.stageRevisionRepairSupport = stageRevisionRepairSupport;
        this.stageStatusSupport = stageStatusSupport;
    }

    public RunRecord rejectHumanReview(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            String reviewer,
            String reason,
            StageTransitionSupport.StageEntryAction stageEntryAction
    ) {
        StageExecution currentExecution = StageStatusSupport.requireStage(runRecord.stageStates(), stageType);
        DocumentLanguage language = languagePolicy.resolve(runRecord.goal(), runRecord.constraints());
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
                new RevisionContext(
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
                        false
                ),
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
            RevisionContext revisionContext,
            StageTransitionSupport.StageEntryAction stageEntryAction
    ) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = StageStatusSupport.requireStage(nextStates, stageType);
        StageExecution reviewedExecution = currentExecution.withStatus(StageStatus.NEEDS_REVISION)
                .withReview(revisionContext.decision(), revisionContext.summary(), revisionContext.changeRequest());
        nextStates.put(stageType, reviewedExecution);
        java.util.Optional<RunRecord> failed =
                stageStatusSupport.applyMaxRevisionGuard(projectPath, runRecord, stageType, currentExecution, nextStates);
        if (failed.isPresent()) {
            return failed.get();
        }

        String revisionNote = stageRevisionRepairSupport.buildRevisionNote(
                projectPath,
                runRecord,
                stageType,
                revisionContext
        );

        RunRecord draft = runRecord.withCurrentStage(revisionContext.rerouteStage(), RunStatus.IN_PROGRESS, nextStates, Instant.now());
        eventLogStore.append(
                projectPath,
                runRecord.runId(),
                WorkflowEventMessages.reroutedForRevision(stageType, revisionContext.rerouteStage(), revisionContext.fixMode())
        );
        return stageEntryAction.enter(draft, revisionContext.rerouteStage(), RunStatus.IN_PROGRESS, revisionNote);
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
}
