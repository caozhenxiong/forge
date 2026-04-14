package devflow.agent.orchestrator;

import devflow.agent.review.ReviewResult;

/**
 * 工具结果 gate 的唯一输出。
 */
public record StageToolResultGateResult(
        ReviewResult reviewResult,
        StageToolResultSummary toolSummary
) {
}
