package devflow.agent.protocol;

import devflow.agent.text.TextCanonicalizer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * review artifact 结构化 payload 的统一读写支撑。
 *
 * <p>协议主路径仍然是 {@link ArtifactBlockKind#REVIEW_RESULT} JSON block。
 * 这里额外兼容历史上已经显式写成 `- key: value` 的机器字段，
 * 只解析固定 key，不从 prose 中猜语义。
 */
public final class ReviewArtifactPayloadSupport {

    private static final Map<String, String> KEY_ALIASES = Map.ofEntries(
            Map.entry("decision", "decision"),
            Map.entry("fixmode", "fixMode"),
            Map.entry("summary", "summary"),
            Map.entry("changerequest", "changeRequest"),
            Map.entry("evidence", "evidence"),
            Map.entry("actionitems", "actionItems"),
            Map.entry("blockingfindings", "blockingFindings"),
            Map.entry("findingcount", "findingCount"),
            Map.entry("exitcode", "exitCode")
    );

    private ReviewArtifactPayloadSupport() {
    }

    public static ReviewArtifactPayload readFirstPayload(String content) {
        ReviewArtifactPayload payload = StructuredArtifactBlocks.readFirstJsonBlock(
                content,
                ArtifactBlockKind.REVIEW_RESULT,
                ReviewArtifactPayload.class
        );
        if (payload != null) {
            return payload;
        }
        return parseExplicitKeyValuePayload(content);
    }

    public static String upsertReviewResultBlock(String content) {
        ReviewArtifactPayload payload = readFirstPayload(content);
        if (payload == null || payload.decision() == null || payload.decision().isBlank()) {
            return content == null ? "" : content;
        }
        return StructuredArtifactBlocks.upsertJsonBlock(
                content,
                ArtifactBlockKind.REVIEW_RESULT,
                payload
        );
    }

    private static ReviewArtifactPayload parseExplicitKeyValuePayload(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("- ")) {
                line = line.substring(2).trim();
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String rawKey = canonicalizeKey(line.substring(0, separator));
            String normalizedKey = KEY_ALIASES.get(rawKey);
            if (normalizedKey == null) {
                continue;
            }
            String value = TextCanonicalizer.collapseWhitespace(line.substring(separator + 1)).trim();
            values.putIfAbsent(normalizedKey, value);
        }
        String decision = blankToNull(values.get("decision"));
        if (decision == null) {
            return null;
        }
        return new ReviewArtifactPayload(
                decision,
                blankToNull(values.get("fixMode")),
                blankToNull(values.get("summary")),
                blankToNull(values.get("changeRequest")),
                blankToNull(values.get("evidence")),
                blankToNull(values.get("actionItems")),
                parseBoolean(values.get("blockingFindings")),
                parseInteger(values.get("findingCount")),
                parseInteger(values.get("exitCode"))
        );
    }

    private static String canonicalizeKey(String key) {
        if (key == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < key.length(); index++) {
            char current = key.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toLowerCase(current));
            }
        }
        return builder.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
