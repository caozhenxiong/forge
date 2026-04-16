package devflow.agent.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RunRecord(
        UUID runId,
        Path projectPath,
        String goal,
        String constraints,
        RunConfig config,
        StageType currentStage,
        RunStatus status,
        Map<StageType, StageExecution> stageStates,
        HumanReviewResolutionContext humanReviewResolutionContext,
        Instant createdAt,
        Instant updatedAt
) {

    public RunRecord(
            UUID runId,
            Path projectPath,
            String goal,
            String constraints,
            RunConfig config,
            StageType currentStage,
            RunStatus status,
            Map<StageType, StageExecution> stageStates,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(runId, projectPath, goal, constraints, config, currentStage, status, stageStates, null, createdAt, updatedAt);
    }

    public RunRecord withCurrentStage(StageType nextStage, RunStatus nextStatus, Map<StageType, StageExecution> nextStates, Instant now) {
        return new RunRecord(
                runId,
                projectPath,
                goal,
                constraints,
                config,
                nextStage,
                nextStatus,
                nextStates,
                humanReviewResolutionContext,
                createdAt,
                now
        );
    }

    public RunRecord withHumanReviewResolutionContext(HumanReviewResolutionContext nextContext) {
        return new RunRecord(
                runId,
                projectPath,
                goal,
                constraints,
                config,
                currentStage,
                status,
                stageStates,
                nextContext,
                createdAt,
                updatedAt
        );
    }
}
