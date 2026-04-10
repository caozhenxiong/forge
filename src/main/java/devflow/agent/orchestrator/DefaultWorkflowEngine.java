package devflow.agent.orchestrator;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.artifact.StageArtifactComposer;
import devflow.agent.context.ContextProjector;
import devflow.agent.executor.GenerationEngine;
import devflow.agent.loop.AgentLoop;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.StageReviewer;
import devflow.agent.supervisor.SupervisorAgent;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultWorkflowEngine implements WorkflowEngine {

    private final WorkflowRunLifecycleSupport runLifecycleSupport;

    public DefaultWorkflowEngine(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            StageArtifactComposer stageArtifactComposer,
            StageReviewer stageReviewer,
            EventLogStore eventLogStore,
            WorkspaceSnapshotStore snapshotStore,
            DiagnosisAgent diagnosisAgent,
            RepairAgent repairAgent,
            SupervisorAgent supervisorAgent,
            FlowController flowController,
            StageFlowPolicy stageFlowPolicy,
            ContextProjector contextProjector,
            AgentLoop agentLoop
    ) {
        WorkflowArtifactRenderer workflowArtifactRenderer = new WorkflowArtifactRenderer();
        StageTransitionSupport stageTransitionSupport = new StageTransitionSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                diagnosisAgent,
                repairAgent,
                stageFlowPolicy,
                workflowArtifactRenderer
        );
        GenerationEngine reviewExecutionEngine = new GenerationEngine();
        StageOperationExecutor stageOperationExecutor = new StageOperationExecutor(
                stageArtifactComposer,
                stageReviewer,
                eventLogStore,
                reviewExecutionEngine,
                new StageOperationPolicy()
        );
        StageEntryExecutor stageEntryExecutor = new StageEntryExecutor(
                runRepository,
                artifactStore,
                eventLogStore,
                stageOperationExecutor,
                stageTransitionSupport
        );
        FlowDecisionExecutor flowDecisionExecutor = new FlowDecisionExecutor(
                stageTransitionSupport,
                stageEntryExecutor
        );
        StageProgressArtifactSupport stageProgressArtifactSupport = new StageProgressArtifactSupport(
                artifactStore,
                eventLogStore,
                workflowArtifactRenderer
        );
        StageProgressCoordinator stageProgressCoordinator = new StageProgressCoordinator(
                artifactStore,
                diagnosisAgent,
                supervisorAgent,
                flowController,
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                stageProgressArtifactSupport,
                new StageToolResultLoader(artifactStore),
                new StageToolResultGuard()
        );
        this.runLifecycleSupport = new WorkflowRunLifecycleSupport(
                runRepository,
                eventLogStore,
                snapshotStore,
                stageTransitionSupport,
                stageEntryExecutor,
                stageProgressCoordinator,
                agentLoop
        );
    }

    public void initialize(Path projectPath) {
        runLifecycleSupport.initialize(projectPath);
    }

    public RunRecord createRun(Path projectPath, String goal, String constraints) {
        return runLifecycleSupport.createRun(projectPath, goal, constraints);
    }

    public RunRecord find(Path projectPath, UUID runId) {
        return runLifecycleSupport.find(projectPath, runId);
    }

    @Override
    public RunRecord startRun(UUID runId) {
        throw new UnsupportedOperationException("Use startRun(projectPath, runId)");
    }

    public RunRecord startRun(Path projectPath, UUID runId) {
        return runLifecycleSupport.startRun(projectPath, runId);
    }

    @Override
    public RunRecord resumeRun(UUID runId) {
        throw new UnsupportedOperationException("Use resumeRun(projectPath, runId)");
    }

    public RunRecord resumeRun(Path projectPath, UUID runId) {
        return runLifecycleSupport.resumeRun(projectPath, runId);
    }

    @Override
    public RunRecord approveStage(UUID runId, StageType stageType, String reviewer) {
        throw new UnsupportedOperationException("Use approveStage(projectPath, runId, stageType, reviewer)");
    }

    public RunRecord approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer) {
        return runLifecycleSupport.approveStage(projectPath, runId, stageType, reviewer);
    }

    @Override
    public RunRecord rejectStage(UUID runId, StageType stageType, String reviewer, String reason) {
        throw new UnsupportedOperationException("Use rejectStage(projectPath, runId, stageType, reviewer, reason)");
    }

    public RunRecord rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason) {
        return runLifecycleSupport.rejectStage(projectPath, runId, stageType, reviewer, reason);
    }
}
