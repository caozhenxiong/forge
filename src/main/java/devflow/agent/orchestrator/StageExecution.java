package devflow.agent.orchestrator;

import devflow.agent.review.ReviewDecision;

public record StageExecution(
        StageType stageType,
        StageStatus status,
        int attempt,
        String artifactPath,
        ReviewDecision reviewDecision,
        String reviewSummary,
        String changeRequest
) {

    public StageExecution withStatus(StageStatus nextStatus) {
        return new StageExecution(stageType, nextStatus, attempt, artifactPath, reviewDecision, reviewSummary, changeRequest);
    }

    public StageExecution withArtifactPath(String nextArtifactPath) {
        return new StageExecution(stageType, status, attempt, nextArtifactPath, reviewDecision, reviewSummary, changeRequest);
    }

    public StageExecution withReview(ReviewDecision nextDecision, String nextSummary, String nextChangeRequest) {
        return new StageExecution(stageType, status, attempt, artifactPath, nextDecision, nextSummary, nextChangeRequest);
    }

    public StageExecution nextAttempt(StageStatus nextStatus) {
        return new StageExecution(stageType, nextStatus, attempt + 1, artifactPath, reviewDecision, reviewSummary, changeRequest);
    }
}

