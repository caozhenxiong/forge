package devflow.agent.executor.subtask;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.gate.ImplementationCompletenessResult;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubtaskRetryFeedbackRendererTests {

    @Test
    void renderPreservesStructuredOverrideChangesForPatchRetry() {
        SubtaskRetryFeedbackRenderer renderer = new SubtaskRetryFeedbackRenderer(new SubtaskReviewPromptAssembler());

        String feedback = renderer.render(
                new SelfCheckResult(false, "self-check failed", "details"),
                new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "需要继续修当前实现",
                        "只修当前文件范围",
                        "evidence",
                        "action",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(new FileChange("src/game.js", ChangeAction.WRITE, "repair gameplay")),
                        null,
                        null
                ),
                ImplementationCompletenessResult.failure(
                        1,
                        0,
                        List.of("not complete"),
                        List.of("missing")
                )
        );

        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(feedback);

        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(), directives.implementationPatchTarget());
        assertEquals(1, directives.overrideChanges().size());
        assertEquals("src/game.js", directives.overrideChanges().getFirst().path());
    }
}
