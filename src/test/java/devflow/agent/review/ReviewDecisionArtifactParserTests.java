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
                                ImplementationPatchTarget.NONE.name(),
                                java.util.List.of(),
                                "PATCH_CURRENT_STAGE",
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
        ReviewResult result = parser.parseDecisionArtifact(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload(
                                "APPROVED",
                                "NONE",
                                ImplementationPatchTarget.NONE.name(),
                                java.util.List.of(),
                                "PATCH_CURRENT_STAGE",
                                "NONE",
                                "测试通过",
                                "",
                                "",
                                "",
                                false,
                                0,
                                0
                        )
                ),
                ReviewDecision.REJECTED,
                "测试失败，需要修复并重新执行。"
        );

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void testArtifactRejectsWhenExitCodeIsNonZero() {
        ReviewResult result = parser.parseDecisionArtifact(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload(
                                "REJECTED",
                                "PATCH",
                                ImplementationPatchTarget.NONE.name(),
                                java.util.List.of(),
                                "PATCH_CURRENT_STAGE",
                                "NONE",
                                "测试失败",
                                "修复测试",
                                "",
                                "",
                                true,
                                1,
                                1
                        )
                ),
                ReviewDecision.REJECTED,
                "测试失败，需要修复并重新执行。"
        );

        assertEquals(ReviewDecision.REJECTED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
    }

    @Test
    void parseDecisionArtifactFallsBackWhenStructuredBlockMissing() {
        ReviewResult result = parser.parseDecisionArtifact(
                """
                ## Findings

                - 无结构化 review block
                """,
                ReviewDecision.REVISION_REQUIRED,
                "默认修改请求"
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertEquals("阶段产物未给出明确 decision。", result.summary());
    }
}
