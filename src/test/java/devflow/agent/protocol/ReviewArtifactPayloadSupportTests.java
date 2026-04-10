package devflow.agent.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewArtifactPayloadSupportTests {

    @Test
    void readsExplicitKeyValuePayloadWhenJsonBlockMissing() {
        ReviewArtifactPayload payload = ReviewArtifactPayloadSupport.readFirstPayload("""
                - decision: APPROVED
                - fixMode: NONE
                - summary: looks good
                - changeRequest:
                - evidence:
                - actionItems:
                - blockingFindings: false
                - findingCount: 0
                """);

        assertNotNull(payload);
        assertEquals("APPROVED", payload.decision());
        assertEquals("NONE", payload.fixMode());
        assertEquals("looks good", payload.summary());
        assertEquals(false, payload.blockingFindings());
        assertEquals(0, payload.findingCount());
    }

    @Test
    void upsertsStructuredBlockFromExplicitKeyValuePayload() {
        String content = ReviewArtifactPayloadSupport.upsertReviewResultBlock("""
                - decision: REVISION_REQUIRED
                - fixMode: PATCH
                - summary: needs fixes
                - changeRequest: fix init
                - evidence: init duplicated
                - actionItems: remove duplicate init
                - blockingFindings: true
                - findingCount: 1

                ## Findings

                - [高] index.html: init duplicated.
                """);

        assertTrue(content.contains(ArtifactBlockKind.REVIEW_RESULT.beginMarker()));
        ReviewArtifactPayload payload = StructuredArtifactBlocks.readFirstJsonBlock(
                content,
                ArtifactBlockKind.REVIEW_RESULT,
                ReviewArtifactPayload.class
        );
        assertNotNull(payload);
        assertEquals("REVISION_REQUIRED", payload.decision());
        assertEquals("PATCH", payload.fixMode());
        assertEquals("fix init", payload.changeRequest());
    }
}
