package devflow.agent.protocol;

/**
 * review artifact 结构化 payload 的统一读取支撑。
 *
 * <p>主链只接受 {@link ArtifactBlockKind#REVIEW_RESULT} JSON block，
 * 不再兼容 key-value 或 prose retrofit。
 */
public final class ReviewArtifactPayloadSupport {

    private ReviewArtifactPayloadSupport() {
    }

    public static ReviewArtifactPayload readFirstPayload(String content) {
        return StructuredArtifactBlocks.readFirstJsonBlock(
                content,
                ArtifactBlockKind.REVIEW_RESULT,
                ReviewArtifactPayload.class
        );
    }
}
