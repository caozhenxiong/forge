package devflow.agent.orchestrator;

import devflow.agent.domain.HumanReviewResolutionContext;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;

public final class TerminalHumanApprovalRejectedException extends IllegalStateException {

    private final RunRecord runRecord;
    private final StageType stageType;
    private final HumanReviewResolutionContext context;
    private final String reviewer;

    public TerminalHumanApprovalRejectedException(
            RunRecord runRecord,
            StageType stageType,
            HumanReviewResolutionContext context,
            String reviewer
    ) {
        super(message(stageType, context));
        this.runRecord = runRecord;
        this.stageType = stageType;
        this.context = context;
        this.reviewer = reviewer == null ? "" : reviewer;
    }

    private static String message(StageType stageType, HumanReviewResolutionContext context) {
        String diagnostic = context == null || context.diagnosticMessage().isBlank()
                ? "当前人工终态不允许再次批准。"
                : context.diagnosticMessage();
        return "Stage " + stageType + " cannot approve terminal human review state: " + diagnostic;
    }

    public RunRecord runRecord() {
        return runRecord;
    }

    public StageType stageType() {
        return stageType;
    }

    public HumanReviewResolutionContext context() {
        return context;
    }

    public String reviewer() {
        return reviewer;
    }
}
