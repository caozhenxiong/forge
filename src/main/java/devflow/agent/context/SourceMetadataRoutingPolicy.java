package devflow.agent.context;

import devflow.agent.text.TextCanonicalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 显式低权重条目的统一路由规则。
 *
 * <p>这里只处理我们自己定义的稳定标签与固定问句形式：
 * 1. 推断 / Inference；
 * 2. 设计选择 / Design Choice；
 * 3. 建议 / Recommendation；
 * 4. 待确认问题 / Open Question；
 * 5. 显式待确认 marker 与直接问句。
 *
 * <p>它不负责从自由 prose 猜测“这句像不像建议”，只在条目已经显式标注或
 * 进入固定问句形式时，决定它应该落到哪个 Source Metadata bucket。
 */
public final class SourceMetadataRoutingPolicy {

    private static final List<LabeledPrefix> LABELED_PREFIXES = List.of(
            new LabeledPrefix(SourceMetadataBucket.INFERENCE, List.of("推断：", "推断:", "Inference:", "Inference：")),
            new LabeledPrefix(SourceMetadataBucket.DESIGN_DECISION, List.of(
                    "设计选择：",
                    "设计选择:",
                    "Design Choice:",
                    "Design Choice："
            )),
            new LabeledPrefix(SourceMetadataBucket.RECOMMENDATION, List.of(
                    "建议：",
                    "建议:",
                    "Recommendation:",
                    "Recommendation："
            )),
            new LabeledPrefix(SourceMetadataBucket.OPEN_QUESTION, List.of(
                    "待确认问题：",
                    "待确认问题:",
                    "Open Question:",
                    "Open Question：",
                    "OpenQuestion:",
                    "OpenQuestion："
            ))
    );

    private static final List<String> PENDING_MARKERS = List.of("待确认", "to be confirmed", "tobeconfirmed");
    private static final List<String> DIRECT_QUESTION_PREFIXES = List.of("是否", "whether");

    private SourceMetadataRoutingPolicy() {
    }

    public static RoutedSourceMetadataItem classify(String item) {
        String collapsed = TextCanonicalizer.collapseWhitespace(item);
        if (collapsed.isBlank()) {
            return null;
        }
        RoutedSourceMetadataItem labeled = classifyLabeledItem(collapsed);
        if (labeled != null) {
            return labeled;
        }
        if (isDirectQuestion(collapsed)) {
            String normalized = trimTrailingQuestionMark(collapsed);
            return normalized.isBlank() ? null : new RoutedSourceMetadataItem(SourceMetadataBucket.OPEN_QUESTION, normalized);
        }
        if (hasPendingMarker(collapsed)) {
            String normalized = stripPendingMarkers(collapsed);
            return normalized.isBlank() ? null : new RoutedSourceMetadataItem(SourceMetadataBucket.OPEN_QUESTION, normalized);
        }
        return null;
    }

    public static boolean shouldStayOutOfContract(String item) {
        return classify(item) != null;
    }

    private static RoutedSourceMetadataItem classifyLabeledItem(String collapsed) {
        for (LabeledPrefix prefix : LABELED_PREFIXES) {
            String matchedPrefix = prefix.match(collapsed);
            if (matchedPrefix == null) {
                continue;
            }
            String normalized = collapsed.substring(matchedPrefix.length()).trim();
            if (prefix.bucket() == SourceMetadataBucket.OPEN_QUESTION) {
                normalized = trimTrailingQuestionMark(normalized);
            }
            return normalized.isBlank() ? null : new RoutedSourceMetadataItem(prefix.bucket(), normalized);
        }
        return null;
    }

    private static boolean hasPendingMarker(String collapsed) {
        String normalized = TextCanonicalizer.removeWhitespace(collapsed).toLowerCase(Locale.ROOT);
        for (String marker : PENDING_MARKERS) {
            if (normalized.contains(TextCanonicalizer.removeWhitespace(marker).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDirectQuestion(String collapsed) {
        if (collapsed.indexOf('?') >= 0 || collapsed.indexOf('？') >= 0) {
            return true;
        }
        String normalized = TextCanonicalizer.removeWhitespace(collapsed).toLowerCase(Locale.ROOT);
        for (String prefix : DIRECT_QUESTION_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String stripPendingMarkers(String collapsed) {
        String normalized = collapsed;
        for (String marker : List.of("（待确认）", "(待确认)", "待确认问题：", "待确认问题:", "待确认：", "待确认:")) {
            normalized = normalized.replace(marker, " ");
        }
        String lower = normalized.toLowerCase(Locale.ROOT)
                .replace("to be confirmed", " ")
                .replace("tobeconfirmed", " ");
        normalized = lower.equals(normalized) ? normalized : lower;
        normalized = TextCanonicalizer.collapseWhitespace(normalized);
        return trimTrailingQuestionMark(normalized);
    }

    private static String trimTrailingQuestionMark(String value) {
        String normalized = value == null ? "" : value.trim();
        while (!normalized.isBlank()) {
            char last = normalized.charAt(normalized.length() - 1);
            if (last == '?' || last == '？') {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
                continue;
            }
            break;
        }
        return normalized;
    }

    public enum SourceMetadataBucket {
        INFERENCE(SourceMetadataKeys.SOFT_INFERENCES),
        DESIGN_DECISION(SourceMetadataKeys.SOFT_DESIGN_DECISIONS),
        RECOMMENDATION(SourceMetadataKeys.SOFT_RECOMMENDATIONS),
        OPEN_QUESTION(SourceMetadataKeys.OPEN_QUESTIONS);

        private final String metadataKey;

        SourceMetadataBucket(String metadataKey) {
            this.metadataKey = metadataKey;
        }

        public String metadataKey() {
            return metadataKey;
        }
    }

    public record RoutedSourceMetadataItem(SourceMetadataBucket bucket, String text) {

        public RoutedSourceMetadataItem {
            text = text == null ? "" : TextCanonicalizer.collapseWhitespace(text);
        }
    }

    private record LabeledPrefix(SourceMetadataBucket bucket, List<String> prefixes) {

        private String match(String content) {
            String lowerContent = content.toLowerCase(Locale.ROOT);
            for (String prefix : prefixes) {
                if (lowerContent.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                    return prefix;
                }
            }
            return null;
        }
    }
}
