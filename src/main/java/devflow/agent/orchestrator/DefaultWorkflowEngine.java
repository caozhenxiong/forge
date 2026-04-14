package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultWorkflowEngine implements WorkflowEngine {

    private final WorkflowRunLifecycleSupport runLifecycleSupport;

    public DefaultWorkflowEngine(
            WorkflowRunLifecycleSupport runLifecycleSupport
    ) {
        this.runLifecycleSupport = runLifecycleSupport;
    }

    @Override
    public void initialize(Path projectPath) {
        runLifecycleSupport.initialize(projectPath);
    }

    @Override
    public RunRecord createRun(Path projectPath, String goal, String constraints) {
        return runLifecycleSupport.createRun(projectPath, goal, constraints);
    }

    @Override
    public RunRecord find(Path projectPath, UUID runId) {
        return runLifecycleSupport.find(projectPath, runId);
    }

    @Override
    public RunRecord startRun(Path projectPath, UUID runId) {
        return runLifecycleSupport.startRun(projectPath, runId);
    }

    @Override
    public RunRecord resumeRun(Path projectPath, UUID runId) {
        return runLifecycleSupport.resumeRun(projectPath, runId);
    }

    @Override
    public RunRecord approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer) {
        return runLifecycleSupport.approveStage(projectPath, runId, stageType, reviewer);
    }

    @Override
    public RunRecord rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason) {
        return runLifecycleSupport.rejectStage(projectPath, runId, stageType, reviewer, reason);
    }
}
