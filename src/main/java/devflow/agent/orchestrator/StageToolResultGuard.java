package devflow.agent.orchestrator;

import devflow.agent.domain.StageType;

import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;

/**
 * 用结构化工具结果保护 review 结论。
 *
 * <p>如果 reviewer 给了放行结果，但测试/验证工具仍明确失败，
 * 这里会把结论降级回当前阶段修订，保证流程优先相信工具结果。
 */
public class StageToolResultGuard {

    public ReviewResult guard(StageType stageType, ReviewResult reviewResult, StageToolResultSummary toolSummary) {
        if (reviewResult == null || toolSummary == null || !toolSummary.blockingFailure()) {
            return reviewResult;
        }
        if (reviewResult.decision() != ReviewDecision.APPROVED) {
            return reviewResult;
        }
        String summary = switch (stageType) {
            case TEST -> "Structured tool results show that the test stage is not actually complete yet.";
            default -> "Structured tool results show that the current stage is not actually complete yet.";
        };
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                summary,
                toolSummary.recommendedAction().isBlank()
                        ? "Use the failed tool results as the source of truth, fix the current stage, and rerun it."
                        : toolSummary.recommendedAction(),
                toolSummary.evidence(),
                toolSummary.failedTools().isEmpty()
                        ? String.join(", ", toolSummary.failureCodes())
                        : String.join(", ", toolSummary.failedTools())
        );
    }
}
