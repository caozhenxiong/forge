package devflow.agent.review;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewDecisionArtifactParserTests {

    private final ReviewDecisionArtifactParser parser = new ReviewDecisionArtifactParser();

    @Test
    void approvedArtifactWithStructuredBlockingFindingsIsDowngraded() {
        ReviewResult result = parser.parseDecisionArtifact(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload(
                                "APPROVED",
                                "NONE",
                                "looks good",
                                "implement update()",
                                "",
                                "",
                                true,
                                1,
                                null
                        )
                ),
                ReviewDecision.REVISION_REQUIRED,
                "默认修改请求"
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
    }

    @Test
    void testArtifactApprovesWhenExitCodeIsZero() {
        ReviewResult result = parser.parseTestArtifact(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload("APPROVED", "NONE", "测试通过", "", "", "", false, 0, 0)
                )
        );

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void testArtifactRejectsWhenExitCodeIsNonZero() {
        ReviewResult result = parser.parseTestArtifact(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload("REJECTED", "PATCH", "测试失败", "修复测试", "", "", true, 1, 1)
                )
        );

        assertEquals(ReviewDecision.REJECTED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
    }

    @Test
    void parseDecisionArtifactSupportsExplicitKeyValuePayload() {
        ReviewResult result = parser.parseDecisionArtifact("""
                - decision: APPROVED
                - fixMode: NONE
                - summary: looks good
                - changeRequest:
                - evidence:
                - actionItems:
                - blockingFindings: false
                - findingCount: 0

                ## Findings

                无
                """, ReviewDecision.REVISION_REQUIRED, "默认修改请求");

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
        assertEquals("looks good", result.summary());
    }
}
