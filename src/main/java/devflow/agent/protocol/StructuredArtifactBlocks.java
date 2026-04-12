package devflow.agent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一维护 artifact machine block 的渲染与解析。
 *
 * <p>这里故意只做“显式 marker + JSON block”协议，
 * 避免流程继续依赖 markdown 正文、标题、自然语言 bullet 或 regex 语义猜测。
 */
public final class StructuredArtifactBlocks {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private StructuredArtifactBlocks() {
    }

    public static String renderJsonBlock(ArtifactBlockKind kind, Object payload) {
        try {
            return kind.beginMarker()
                    + "\n"
                    + OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                    + "\n"
                    + kind.endMarker();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render structured artifact block for " + kind, exception);
        }
    }

    public static <T> T readFirstJsonBlock(String content, ArtifactBlockKind kind, Class<T> type) {
        List<T> values = readAllJsonBlocks(content, kind, type);
        return values.isEmpty() ? null : values.getFirst();
    }

    public static <T> List<T> readAllJsonBlocks(String content, ArtifactBlockKind kind, Class<T> type) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<T> values = new ArrayList<>();
        int cursor = 0;
        while (cursor < content.length()) {
            int begin = content.indexOf(kind.beginMarker(), cursor);
            if (begin < 0) {
                break;
            }
            int jsonStart = begin + kind.beginMarker().length();
            int end = content.indexOf(kind.endMarker(), jsonStart);
            if (end < 0) {
                break;
            }
            String json = content.substring(jsonStart, end).trim();
            if (!json.isBlank()) {
                try {
                    values.add(OBJECT_MAPPER.readValue(json, type));
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to parse structured artifact block for " + kind, exception);
                }
            }
            cursor = end + kind.endMarker().length();
        }
        return List.copyOf(values);
    }

    public static String stripAllKnownBlocks(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String stripped = content;
        for (ArtifactBlockKind kind : ArtifactBlockKind.values()) {
            stripped = stripKindBlocks(stripped, kind);
        }
        return stripped;
    }

    public static String collectAllKnownBlocks(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        while (cursor < content.length()) {
            RawBlockMatch next = nextRawBlock(content, cursor);
            if (next == null) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(content, next.beginOffset(), next.endOffset());
            cursor = next.endOffset();
        }
        return builder.toString().trim();
    }

    public static String upsertJsonBlock(String content, ArtifactBlockKind kind, Object payload) {
        String stripped = stripKindBlocks(content == null ? "" : content, kind).strip();
        String block = renderJsonBlock(kind, payload);
        if (stripped.isBlank()) {
            return block;
        }
        return stripped + "\n\n" + block;
    }

    private static String stripKindBlocks(String content, ArtifactBlockKind kind) {
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        while (cursor < content.length()) {
            int begin = content.indexOf(kind.beginMarker(), cursor);
            if (begin < 0) {
                builder.append(content.substring(cursor));
                break;
            }
            builder.append(content, cursor, begin);
            int end = content.indexOf(kind.endMarker(), begin + kind.beginMarker().length());
            if (end < 0) {
                break;
            }
            cursor = end + kind.endMarker().length();
        }
        return builder.toString().strip();
    }

    private static RawBlockMatch nextRawBlock(String content, int cursor) {
        int bestBegin = -1;
        ArtifactBlockKind bestKind = null;
        for (ArtifactBlockKind kind : ArtifactBlockKind.values()) {
            int begin = content.indexOf(kind.beginMarker(), cursor);
            if (begin < 0) {
                continue;
            }
            if (bestBegin < 0 || begin < bestBegin) {
                bestBegin = begin;
                bestKind = kind;
            }
        }
        if (bestKind == null) {
            return null;
        }
        int end = content.indexOf(bestKind.endMarker(), bestBegin + bestKind.beginMarker().length());
        if (end < 0) {
            return null;
        }
        return new RawBlockMatch(bestBegin, end + bestKind.endMarker().length());
    }

    private record RawBlockMatch(int beginOffset, int endOffset) {
    }
}
