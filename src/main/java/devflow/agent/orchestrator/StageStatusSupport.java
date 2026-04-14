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
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一维护阶段状态的确定性变更。
 *
 * <p>这层只负责：
 * 1. 读取当前 RunRecord 并生成新的阶段状态
 * 2. 把 review history / run 状态 / fatal 收尾一致地写回仓库
 * 3. 在需要跳转到下一阶段时调用统一的 enterStage 回调
 *
 * <p>它不负责：
 * 1. 决定是否应该回退或重试
 * 2. 生成修订说明
 * 3. diagnosis / repair 相关逻辑
 */
public class StageStatusSupport {

    private final FileRunRepository runRepository;
    private final FileArtifactStore artifactStore;
    private final EventLogStore eventLogStore;
    private final StageFlowPolicy stageFlowPolicy;
    private final WorkflowArtifactRenderer workflowArtifactRenderer;
    private final LanguagePolicy languagePolicy;

    public StageStatusSupport(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageFlowPolicy stageFlowPolicy,
            WorkflowArtifactRenderer workflowArtifactRenderer
    ) {
        this(runRepository, artifactStore, eventLogStore, stageFlowPolicy, workflowArtifactRenderer, new LanguagePolicy());
    }

    public StageStatusSupport(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageFlowPolicy stageFlowPolicy,
            WorkflowArtifactRenderer workflowArtifactRenderer,
            LanguagePolicy languagePolicy
    ) {
        this.runRepository = runRepository;
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
        this.stageFlowPolicy = stageFlowPolicy;
        this.workflowArtifactRenderer = workflowArtifactRenderer;
        this.languagePolicy = languagePolicy;
    }

    public RunRecord approveHumanReview(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            String reviewer,
            StageTransitionSupport.StageEntryAction stageEntryAction
    ) {
        Map<StageType, StageExecution> nextStates = mutableStageStates(runRecord);
        StageExecution currentExecution = requireStage(nextStates, stageType);
        if (currentExecution.status() != StageStatus.AWAITING_HUMAN_REVIEW) {
            throw new IllegalStateException("Stage is not awaiting human review: " + stageType);
        }

        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.APPROVED)
                        .withReview(ReviewDecision.APPROVED, "Approved by " + reviewer, "")
        );
        DocumentLanguage language = languagePolicy.resolve(runRecord.goal(), runRecord.constraints());
        artifactStore.appendReviewHistory(
                projectPath,
                runRecord.runId(),
                stageType,
                workflowArtifactRenderer.renderReviewHistoryEntry(
                        stageType,
                        currentExecution.attempt(),
                        "human:" + reviewer,
                        new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "Approved by " + reviewer, ""),
                        language
                )
        );
        eventLogStore.append(projectPath, runRecord.runId(), WorkflowEventMessages.humanApproved(stageType, reviewer));

        StageType nextStage = stageFlowPolicy.nextStage(stageType);
        Instant now = Instant.now();
        if (nextStage == null) {
            RunRecord completed = runRecord.withCurrentStage(stageType, RunStatus.COMPLETED, nextStates, now);
            return runRepository.save(completed);
        }

        RunRecord draft = runRecord.withCurrentStage(nextStage, RunStatus.IN_PROGRESS, nextStates, now);
        return stageEntryAction.enter(draft, nextStage, RunStatus.IN_PROGRESS, "由人工批准 " + stageType + " 后进入当前阶段。");
    }

    public RunRecord onStageApproved(
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult,
            StageType nextStage,
            String note,
            StageTransitionSupport.StageEntryAction stageEntryAction
    ) {
        Map<StageType, StageExecution> nextStates = mutableStageStates(runRecord);
        StageExecution currentExecution = requireStage(nextStates, stageType);
        Instant now = Instant.now();

        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.APPROVED)
                        .withReview(reviewResult.decision(), reviewResult.summary(), reviewResult.changeRequest())
        );
        if (nextStage == null) {
            RunRecord completed = runRecord.withCurrentStage(stageType, RunStatus.COMPLETED, nextStates, now);
            return runRepository.save(completed);
        }

        RunRecord draft = runRecord.withCurrentStage(nextStage, RunStatus.IN_PROGRESS, nextStates, now);
        return stageEntryAction.enter(draft, nextStage, RunStatus.IN_PROGRESS, note);
    }

    public RunRecord blockForHumanReview(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        Map<StageType, StageExecution> nextStates = mutableStageStates(runRecord);
        StageExecution currentExecution = requireStage(nextStates, stageType);
        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.AWAITING_HUMAN_REVIEW)
                        .withReview(reviewResult.decision(), reviewResult.summary(), reviewResult.changeRequest())
        );
        RunRecord blocked = runRecord.withCurrentStage(stageType, RunStatus.BLOCKED, nextStates, Instant.now());
        return runRepository.save(blocked);
    }

    public RunRecord completeRun(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        Map<StageType, StageExecution> nextStates = mutableStageStates(runRecord);
        StageExecution currentExecution = requireStage(nextStates, stageType);
        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.APPROVED)
                        .withReview(reviewResult.decision(), reviewResult.summary(), reviewResult.changeRequest())
        );
        return runRepository.save(runRecord.withCurrentStage(stageType, RunStatus.COMPLETED, nextStates, Instant.now()));
    }

    public RunRecord failRun(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        Map<StageType, StageExecution> nextStates = mutableStageStates(runRecord);
        StageExecution currentExecution = requireStage(nextStates, stageType);
        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.FAILED)
                        .withReview(reviewResult.decision(), reviewResult.summary(), reviewResult.changeRequest())
        );
        return runRepository.save(runRecord.withCurrentStage(stageType, RunStatus.FAILED, nextStates, Instant.now()));
    }

    /**
     * 致命错误收尾必须幂等，避免同一异常在多层 catch 中重复把 run 写坏。
     */
    public void markFatalFailure(Path projectPath, UUID runId, StageType stageType, RuntimeException ex) {
        RunRecord latest = runRepository.findById(projectPath, runId).orElse(null);
        if (latest == null || latest.status() == RunStatus.FAILED || latest.status() == RunStatus.COMPLETED || latest.status() == RunStatus.CANCELLED) {
            return;
        }
        Map<StageType, StageExecution> nextStates = mutableStageStates(latest);
        StageExecution currentExecution = requireStage(nextStates, stageType);
        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.FAILED)
                        .withReview(
                                ReviewDecision.REJECTED,
                                "Stage failed due to runtime exception",
                                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                        )
        );
        eventLogStore.append(projectPath, runId, WorkflowEventMessages.fatalFailure(stageType, ex.getMessage()));
        runRepository.save(latest.withCurrentStage(stageType, RunStatus.FAILED, nextStates, Instant.now()));
    }

    private Map<StageType, StageExecution> mutableStageStates(RunRecord runRecord) {
        return new EnumMap<>(runRecord.stageStates());
    }

    private StageExecution requireStage(Map<StageType, StageExecution> stageStates, StageType stageType) {
        StageExecution stageExecution = stageStates.get(stageType);
        if (stageExecution == null) {
            throw new IllegalArgumentException("Missing stage state for " + stageType);
        }
        return stageExecution;
    }
}
