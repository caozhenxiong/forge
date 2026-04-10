package devflow.agent.orchestrator;

import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageToolResultGuardTests {

    @Test
    void approvedReviewIsDowngradedWhenBlockingToolFailureExists() {
        StageToolResultGuard guard = new StageToolResultGuard();
        ReviewResult guarded = guard.guard(
                StageType.TEST,
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", ""),
                new StageToolResultSummary(
                        true,
                        1,
                        List.of("TEST_CASE_EXECUTION"),
                        List.of("TEST_CASE_EXECUTION_FAILED"),
                        "tool summary",
                        "execution failed",
                        "rerun tests"
                )
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, guarded.decision());
        assertEquals(FixMode.PATCH, guarded.fixMode());
        assertEquals("execution failed", guarded.evidence());
        assertEquals("rerun tests", guarded.changeRequest());
    }
}
