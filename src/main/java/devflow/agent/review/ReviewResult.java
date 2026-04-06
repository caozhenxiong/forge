package devflow.agent.review;

public record ReviewResult(
        ReviewDecision decision,
        FixMode fixMode,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems
) {
    public ReviewResult(ReviewDecision decision, FixMode fixMode, String summary, String changeRequest) {
        this(decision, fixMode, summary, changeRequest, "", "");
    }
}
