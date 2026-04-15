package devflow.agent.executor.subtask;

import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewSemantics;
import devflow.agent.review.StructuredReviewResult;
import devflow.agent.review.SubtaskBoundaryReviewPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtaskBoundaryGateTests {

    @Test
    void rejectsWhenStructuredPayloadMarksDeferredCapabilityViolation() {
        SubtaskBoundaryGate gate = new SubtaskBoundaryGate();
        StructuredReviewResult structured = new StructuredReviewResult(
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", ""),
                ReviewSemantics.empty(),
                new SubtaskBoundaryReviewPayload(
                        true,
                        true,
                        false,
                        "当前子任务提前实现了后续 gameplay 能力。",
                        "发现计分与消行逻辑已经落在当前 skeleton 子任务里。",
                        "移除越界实现，只保留页面壳体与最小 bootstrapping。",
                        List.of("index.html")
                )
        );

        ReviewResult review = gate.enforce(subtask(), structured, DocumentLanguage.ZH);

        assertEquals(ReviewDecision.REVISION_REQUIRED, review.decision());
        assertEquals(FixMode.PATCH, review.fixMode());
        assertEquals(ReviewReasonCode.CONTRACT_BOUNDARY_VIOLATION, review.reasonCode());
        assertTrue(review.summary().contains("后续 gameplay"));
        assertTrue(review.changeRequest().contains("页面壳体"));
        assertEquals(devflow.agent.review.ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, review.implementationPatchTarget());
    }

    @Test
    void passesThroughWhenBoundaryPayloadIsAbsent() {
        SubtaskBoundaryGate gate = new SubtaskBoundaryGate();
        ReviewResult approved = new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");

        ReviewResult review = gate.enforce(
                subtask(),
                new StructuredReviewResult(approved, ReviewSemantics.empty()),
                DocumentLanguage.ZH
        );

        assertEquals(approved, review);
    }

    private Subtask subtask() {
        return new Subtask(
                "搭壳体",
                "只做页面壳体",
                List.of(),
                List.of("页面壳体"),
                List.of("gameplay"),
                List.of("页面可打开"),
                true,
                DeliveryMode.SKELETON,
                List.of(new FileChange("index.html", devflow.agent.executor.ChangeAction.WRITE, "创建入口"))
        );
    }
}
