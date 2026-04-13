package devflow.agent.orchestrator;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.loop.AgentLoop;
import devflow.agent.loop.LoopStepResult;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一维护 run 生命周期入口。
 *
 * <p>这层负责：
 * 1. create / find / start / resume / approve / reject
 * 2. 基于 AgentLoop 驱动 progress
 * 3. 在入口层统一做 fatal 收尾
 *
 * <p>这样 DefaultWorkflowEngine 可以退化成真正的 facade，
 * 只负责依赖装配与接口暴露，而不是继续混合运行生命周期细节。
 */
public class WorkflowRunLifecycleSupport {

    private final FileRunRepository runRepository;
    private final EventLogStore eventLogStore;
    private final WorkspaceSnapshotStore snapshotStore;
    private final StageTransitionSupport stageTransitionSupport;
    private final StageEntryExecutor stageEntryExecutor;
    private final StageProgressCoordinator stageProgressCoordinator;
    private final AgentLoop agentLoop;

    public WorkflowRunLifecycleSupport(
            FileRunRepository runRepository,
            EventLogStore eventLogStore,
            WorkspaceSnapshotStore snapshotStore,
            StageTransitionSupport stageTransitionSupport,
            StageEntryExecutor stageEntryExecutor,
            StageProgressCoordinator stageProgressCoordinator,
            AgentLoop agentLoop
    ) {
        this.runRepository = runRepository;
        this.eventLogStore = eventLogStore;
        this.snapshotStore = snapshotStore;
        this.stageTransitionSupport = stageTransitionSupport;
        this.stageEntryExecutor = stageEntryExecutor;
        this.stageProgressCoordinator = stageProgressCoordinator;
        this.agentLoop = agentLoop;
    }

    public void initialize(Path projectPath) {
        runRepository.initialize(projectPath);
    }

    public RunRecord createRun(Path projectPath, String goal, String constraints) {
        runRepository.initialize(projectPath);
        Instant now = Instant.now();
        UUID runId = UUID.randomUUID();
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        RunRecord runRecord = new RunRecord(
                runId,
                projectPath,
                goal,
                constraints == null ? "" : constraints,
                RunConfig.defaultConfig(),
                StageType.ANALYSIS,
                RunStatus.CREATED,
                stageStates,
                now,
                now
        );
        RunRecord saved = runRepository.save(runRecord);
        snapshotStore.captureBaseline(projectPath, runId);
        eventLogStore.append(projectPath, runId, WorkflowEventMessages.runCreated());
        return saved;
    }

    public RunRecord find(Path projectPath, UUID runId) {
        return runRepository.findById(projectPath, runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    public RunRecord startRun(Path projectPath, UUID runId) {
        RunRecord runRecord = find(projectPath, runId);
        try {
            RunRecord started = stageEntryExecutor.enterStage(runRecord, StageType.ANALYSIS, RunStatus.IN_PROGRESS, "初次启动工作流。");
            return progress(projectPath, started);
        } catch (RuntimeException ex) {
            stageTransitionSupport.markFatalFailure(projectPath, runId, StageType.ANALYSIS, ex);
            throw ex;
        }
    }

    public RunRecord resumeRun(Path projectPath, UUID runId) {
        RunRecord runRecord = find(projectPath, runId);
        if (runRecord.status() == RunStatus.COMPLETED || runRecord.status() == RunStatus.FAILED || runRecord.status() == RunStatus.CANCELLED) {
            return runRecord;
        }

        try {
            StageExecution currentExecution = requireStage(runRecord.stageStates(), runRecord.currentStage());
            if (currentExecution.status() == StageStatus.PENDING || currentExecution.artifactPath() == null) {
                runRecord = stageEntryExecutor.enterStage(runRecord, runRecord.currentStage(), RunStatus.IN_PROGRESS, "恢复执行。");
            }
            return progress(projectPath, runRecord);
        } catch (RuntimeException ex) {
            stageTransitionSupport.markFatalFailure(projectPath, runId, runRecord.currentStage(), ex);
            throw ex;
        }
    }

    public RunRecord approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer) {
        RunRecord runRecord = find(projectPath, runId);
        RunRecord entered = stageTransitionSupport.approveHumanReview(
                projectPath,
                runRecord,
                stageType,
                reviewer,
                stageEntryExecutor::enterStage
        );
        return progress(projectPath, entered);
    }

    public RunRecord rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason) {
        RunRecord runRecord = find(projectPath, runId);
        RunRecord rerouted = stageTransitionSupport.rejectHumanReview(
                projectPath,
                runRecord,
                stageType,
                reviewer,
                reason,
                stageEntryExecutor::enterStage
        );
        return progress(projectPath, rerouted);
    }

    private RunRecord progress(Path projectPath, RunRecord runRecord) {
        try {
            return agentLoop.runUntilStable(runRecord, loopState -> progressOnce(projectPath, loopState));
        } catch (RuntimeException ex) {
            stageTransitionSupport.markFatalFailure(projectPath, runRecord.runId(), runRecord.currentStage(), ex);
            throw ex;
        }
    }

    private LoopStepResult progressOnce(Path projectPath, devflow.agent.loop.LoopState loopState) {
        return stageProgressCoordinator.progress(projectPath, loopState.runRecord());
    }

    private StageExecution requireStage(Map<StageType, StageExecution> stageStates, StageType stageType) {
        StageExecution stageExecution = stageStates.get(stageType);
        if (stageExecution == null) {
            throw new IllegalArgumentException("Missing stage state for " + stageType);
        }
        return stageExecution;
    }
}
