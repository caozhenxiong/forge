package devflow.agent.review;

/**
 * implementation subtask review 专用的边界语义载荷。
 *
 * <p>它只描述当前子任务是否越过 capability boundary，不进入全局 ReviewResult 主协议。
 */
public record SubtaskBoundaryReviewPayload(
        boolean provided,
        boolean implementsDeferredCapabilities,
        boolean implementsForeignCapabilities,
        String summary,
        String evidence,
        String actionItems
) {

    public static SubtaskBoundaryReviewPayload empty() {
        return new SubtaskBoundaryReviewPayload(false, false, false, "", "", "");
    }

    public boolean boundaryViolation() {
        return implementsDeferredCapabilities || implementsForeignCapabilities;
    }
}
