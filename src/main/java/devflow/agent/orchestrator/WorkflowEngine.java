package devflow.agent.orchestrator;

import java.util.UUID;

public interface WorkflowEngine {

    RunRecord startRun(UUID runId);

    RunRecord resumeRun(UUID runId);

    RunRecord approveStage(UUID runId, StageType stageType, String reviewer);

    RunRecord rejectStage(UUID runId, StageType stageType, String reviewer, String reason);
}

