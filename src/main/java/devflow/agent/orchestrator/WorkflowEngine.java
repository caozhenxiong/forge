package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;

import java.nio.file.Path;
import java.util.UUID;

public interface WorkflowEngine {

    void initialize(Path projectPath);

    RunRecord createRun(Path projectPath, String goal, String constraints);

    RunRecord find(Path projectPath, UUID runId);

    RunRecord startRun(Path projectPath, UUID runId);

    RunRecord resumeRun(Path projectPath, UUID runId);

    RunRecord approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer);

    RunRecord rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason);
}
