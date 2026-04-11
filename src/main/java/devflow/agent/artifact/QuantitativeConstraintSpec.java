package devflow.agent.artifact;

import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.text.TextCanonicalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 集中定义文档阶段可识别的量化约束 grammar。
 *
 * <p>这里处理的是我们自己允许进入主链的稳定量化领域：
 * 页面加载时间、交互/输入响应时间，以及其他带单位的数值门槛。
 * 解析结果只服务 authority 对齐和规范化输出，不做开放 prose 语义猜测。
 */
final class QuantitativeConstraintSpec {

    private static final Pattern NUMBER_AND_UNIT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(ms|毫秒|秒|s|fps)", Pattern.CASE_INSENSITIVE);
    private static final List<String> PAGE_LOAD_TOKENS = List.of("页面加载", "加载时间", "page load", "load time");
    private static final List<String> INTERACTION_TOKENS = List.of(
            "交互响应",
            "响应时间",
            "响应延迟",
            "输入延迟",
            "input latency",
            "response time",
            "response latency",
            "interaction latency"
    );
    private static final List<String> COMPARATOR_TOKENS = List.of("不超过", "小于", "<=", "<", "within", "under", "less than", "at most", "以上", ">=", ">", "至少");
    private static final List<String> UNIT_TOKENS = List.of("ms", "毫秒", "秒", "fps");

    QuantitativeConstraintMatch analyze(String value) {
        String normalized = normalizeAuthorityItem(value);
        if (normalized.isBlank() || !containsDigit(normalized) || !containsMeasurementHint(normalized)) {
            return QuantitativeConstraintMatch.nonQuantitative(normalized);
        }
        QuantitativeConstraintMetric metric = detectMetric(normalized);
        Integer thresholdMs = switch (metric) {
            case PAGE_LOAD_MAX, INTERACTION_MAX -> parseMilliseconds(normalized);
            default -> null;
        };
        return new QuantitativeConstraintMatch(true, metric, thresholdMs, normalized);
    }

    boolean isQuantitativeAuthorityItem(String value) {
        return analyze(value).quantitative();
    }

    String normalizeAuthorityItem(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = stripListMarker(value);
        normalized = TextCanonicalizer.collapseWhitespace(normalized);
        normalized = trimTrailingPunctuation(normalized).toLowerCase(Locale.ROOT);
        return normalized;
    }

    String projectValidationLine(
            QuantitativeConstraintMetric metric,
            ValidationMetadata validationMetadata,
            DocumentLanguage language
    ) {
        if (validationMetadata == null) {
            return null;
        }
        if (metric == QuantitativeConstraintMetric.PAGE_LOAD_MAX && validationMetadata.pageLoadMaxMs() != null) {
            return language.isChinese()
                    ? "页面加载时间不超过 " + formatChineseDuration(validationMetadata.pageLoadMaxMs())
                    : "Page load time stays within " + formatEnglishDuration(validationMetadata.pageLoadMaxMs());
        }
        if (metric == QuantitativeConstraintMetric.INTERACTION_MAX && validationMetadata.interactionMaxMs() != null) {
            return language.isChinese()
                    ? "交互响应时间不超过 " + formatChineseDuration(validationMetadata.interactionMaxMs())
                    : "Interaction response time stays within " + formatEnglishDuration(validationMetadata.interactionMaxMs());
        }
        return null;
    }

    boolean matchesValidationThreshold(QuantitativeConstraintMatch match, ValidationMetadata validationMetadata) {
        if (match == null || !match.quantitative() || validationMetadata == null) {
            return false;
        }
        if (match.metric() == QuantitativeConstraintMetric.PAGE_LOAD_MAX) {
            return match.thresholdMs() != null && match.thresholdMs().equals(validationMetadata.pageLoadMaxMs());
        }
        if (match.metric() == QuantitativeConstraintMetric.INTERACTION_MAX) {
            return match.thresholdMs() != null && match.thresholdMs().equals(validationMetadata.interactionMaxMs());
        }
        return false;
    }

    private QuantitativeConstraintMetric detectMetric(String normalized) {
        if (containsAny(normalized, PAGE_LOAD_TOKENS)) {
            return QuantitativeConstraintMetric.PAGE_LOAD_MAX;
        }
        if (containsAny(normalized, INTERACTION_TOKENS)) {
            return QuantitativeConstraintMetric.INTERACTION_MAX;
        }
        return QuantitativeConstraintMetric.OTHER_NUMERIC;
    }

    private Integer parseMilliseconds(String normalized) {
        Matcher matcher = NUMBER_AND_UNIT.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        if (unit.equals("秒") || unit.equals("s")) {
            return (int) Math.round(value * 1000d);
        }
        if (unit.equals("ms") || unit.equals("毫秒")) {
            return (int) Math.round(value);
        }
        return null;
    }

    private String stripListMarker(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("- [ ] ")) {
            return trimmed.substring(6).trim();
        }
        if (trimmed.startsWith("* [ ] ")) {
            return trimmed.substring(6).trim();
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            return trimmed.substring(2).trim();
        }
        return trimmed;
    }

    private String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0) {
            char current = value.charAt(end - 1);
            if (current == '.' || current == '。' || current == ';' || current == '；') {
                end--;
                continue;
            }
            break;
        }
        return value.substring(0, end).trim();
    }

    private boolean containsDigit(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsMeasurementHint(String normalized) {
        return containsAny(normalized, COMPARATOR_TOKENS) || containsAny(normalized, UNIT_TOKENS);
    }

    private boolean containsAny(String normalized, List<String> tokens) {
        for (String token : tokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String formatChineseDuration(int milliseconds) {
        if (milliseconds >= 1000 && milliseconds % 1000 == 0) {
            return (milliseconds / 1000) + " 秒";
        }
        return milliseconds + " ms";
    }

    private String formatEnglishDuration(int milliseconds) {
        if (milliseconds >= 1000 && milliseconds % 1000 == 0) {
            return (milliseconds / 1000) + " s";
        }
        return milliseconds + " ms";
    }

    enum QuantitativeConstraintMetric {
        PAGE_LOAD_MAX,
        INTERACTION_MAX,
        OTHER_NUMERIC
    }

    record QuantitativeConstraintMatch(
            boolean quantitative,
            QuantitativeConstraintMetric metric,
            Integer thresholdMs,
            String normalizedAuthorityItem
    ) {

        static QuantitativeConstraintMatch nonQuantitative(String normalizedAuthorityItem) {
            return new QuantitativeConstraintMatch(false, null, null, normalizedAuthorityItem);
        }
    }
}
