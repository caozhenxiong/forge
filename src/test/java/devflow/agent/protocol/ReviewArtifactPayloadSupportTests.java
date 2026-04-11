package devflow.agent.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewArtifactPayloadSupportTests {

    @Test
    void readsStructuredReviewResultBlock() {
        ReviewArtifactPayload payload = ReviewArtifactPayloadSupport.readFirstPayload(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_RESULT,
                        new ReviewArtifactPayload(
                                "APPROVED",
                                "NONE",
                                "NONE",
                                java.util.List.of(),
                                "PATCH_CURRENT_STAGE",
                                "NONE",
                                "looks good",
                                "",
                                "",
                                "",
                                false,
                                0,
                                null
                        )
                )
        );

        assertEquals("APPROVED", payload.decision());
        assertEquals("NONE", payload.fixMode());
        assertEquals("looks good", payload.summary());
    }

    @Test
    void returnsNullWhenStructuredBlockMissing() {
        assertNull(ReviewArtifactPayloadSupport.readFirstPayload("""
                ## Findings

                - 无阻塞问题
                """));
    }
}
