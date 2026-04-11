package devflow.agent.orchestrator;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * 负责“进入阶段”这一件事：
 * 1. 推进阶段 attempt 与运行状态
 * 2. 触发阶段产物生成
 * 3. 持久化 artifactPath
 *
 * 这样 DefaultWorkflowEngine 不再同时承担状态推进和阶段产物执行两类职责。
 */
public class StageEntryExecutor {

    private final FileRunRepository runRepository;
    private final FileArtifactStore artifactStore;
    private final EventLogStore eventLogStore;
    private final StageOperationExecutor stageOperationExecutor;
    private final StageTransitionSupport stageTransitionSupport;

    public StageEntryExecutor(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageOperationExecutor stageOperationExecutor,
            StageTransitionSupport stageTransitionSupport
    ) {
        this.runRepository = runRepository;
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
        this.stageOperationExecutor = stageOperationExecutor;
        this.stageTransitionSupport = stageTransitionSupport;
    }

    /**
     * “Entered stage” 必须在真正执行阶段工作前先落盘，
     * 否则后续 implementation/test 的子事件可能插队，看起来像阶段时间线乱序。
     */
    public RunRecord enterStage(RunRecord runRecord, StageType stageType, RunStatus runStatus, String note) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution nextExecution = requireStage(nextStates, stageType).nextAttempt(StageStatus.RUNNING);
        nextStates.put(
                stageType,
                nextExecution.withArtifactPath(null)
                        .withReview(null, null, null)
        );
        RunRecord draft = runRecord.withCurrentStage(stageType, runStatus, nextStates, Instant.now());
        RunRecord persistedRunning = runRepository.save(draft);
        eventLogStore.append(runRecord.projectPath(), runRecord.runId(), WorkflowEventMessages.enteringStage(stageType, nextExecution.attempt()));
        eventLogStore.append(runRecord.projectPath(), runRecord.runId(), WorkflowEventMessages.enteredStage(stageType, nextExecution.attempt()));

        try {
            String artifactContent = stageOperationExecutor.composeStageArtifact(
                    runRecord.projectPath(),
                    persistedRunning,
                    stageType,
                    nextExecution,
                    note
            );
            String artifactPath = artifactStore.writeArtifact(runRecord.projectPath(), runRecord.runId(), stageType, artifactContent).toString();
            artifactStore.writeAttemptScopedAuxiliaryArtifact(
                    runRecord.projectPath(),
                    runRecord.runId(),
                    AuxiliaryArtifactNames.stageDirective(stageType),
                    nextExecution.attempt(),
                    note == null ? "" : note.trim()
            );
            nextStates.put(
                    stageType,
                    nextExecution.withArtifactPath(artifactPath)
                            .withReview(null, null, null)
            );
            eventLogStore.append(
                    runRecord.projectPath(),
                    runRecord.runId(),
                    WorkflowEventMessages.stageArtifactGenerated(stageType, nextExecution.attempt())
            );
            RunRecord saved = persistedRunning.withCurrentStage(stageType, runStatus, nextStates, Instant.now());
            return runRepository.save(saved);
        } catch (RuntimeException ex) {
            stageTransitionSupport.markFatalFailure(runRecord.projectPath(), runRecord.runId(), stageType, ex);
            throw ex;
        }
    }

    private StageExecution requireStage(Map<StageType, StageExecution> stageStates, StageType stageType) {
        StageExecution stageExecution = stageStates.get(stageType);
        if (stageExecution == null) {
            throw new IllegalArgumentException("Missing stage state for " + stageType);
        }
        return stageExecution;
    }
}
