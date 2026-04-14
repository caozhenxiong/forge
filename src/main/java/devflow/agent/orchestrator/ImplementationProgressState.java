package devflow.agent.orchestrator;

import devflow.agent.review.ReviewResult;

/**
 * implementation 进度对 coordinator 的单一投影视图。
 */
public record ImplementationProgressState(
        boolean stageReady,
        boolean blocked,
        String reviewSummary,
        StageContinuationContext continuationContext,
        ReviewResult humanReviewResult
) {

    public static ImplementationProgressState ready(String reviewSummary) {
        return new ImplementationProgressState(true, false, reviewSummary, null, null);
    }

    public static ImplementationProgressState continuing(StageContinuationContext continuationContext) {
        return new ImplementationProgressState(false, false, null, continuationContext, null);
    }

    public static ImplementationProgressState blocked(
            StageContinuationContext continuationContext,
            ReviewResult humanReviewResult
    ) {
        return new ImplementationProgressState(false, true, null, continuationContext, humanReviewResult);
    }
}
