package devflow.agent.orchestrator;

import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.ReviewResult;

/**
 * implementation 进度对 coordinator 的单一投影视图。
 */
public record ImplementationProgressState(
        boolean stageReady,
        String reviewSummary,
        StageContinuationContext continuationContext,
        ReviewResult humanReviewResult
) {

    public static ImplementationProgressState ready(String reviewSummary) {
        return new ImplementationProgressState(true, reviewSummary, null, null);
    }

    public static ImplementationProgressState continuing(StageContinuationContext continuationContext) {
        return new ImplementationProgressState(false, null, continuationContext, null);
    }

    public static ImplementationProgressState blocked(
            StageContinuationContext continuationContext,
            ReviewResult humanReviewResult
    ) {
        return new ImplementationProgressState(false, null, continuationContext, humanReviewResult);
    }

    public ImplementationContinuationMode continuationMode() {
        return continuationContext == null
                ? ImplementationContinuationMode.MID_PLAN_CONTINUE
                : continuationContext.continuationMode();
    }

    public boolean blockedForHumanReview() {
        return !stageReady && continuationMode().blocked();
    }

    public boolean autoContinue() {
        return !stageReady && continuationMode().autoContinue();
    }
}
