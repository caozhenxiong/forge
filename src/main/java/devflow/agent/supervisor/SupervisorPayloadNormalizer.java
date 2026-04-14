package devflow.agent.supervisor;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.domain.StageType;
import devflow.agent.review.FixMode;
import devflow.agent.util.EnumParsers;
import java.util.ArrayList;
import java.util.List;

/**
 * supervisor payload 归一化支持。
 *
 * <p>只负责：
 * 1. stage/fixMode 等基础字段解析；
 * 2. guidance 列表去空白；
 * 3. placeholder/null literal 清洗。
 */
public final class SupervisorPayloadNormalizer {

    List<String> normalizeList(List<String> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : rawItems) {
            if (item == null || item.isBlank()) {
                continue;
            }
            items.add(item.trim());
        }
        return items;
    }

    StageType parseStage(String value) {
        if (value == null || value.isBlank() || PlaceholderValues.isNullLiteral(value)) {
            return null;
        }
        return EnumParsers.parseIgnoreCase(StageType.class, value, null);
    }

    FixMode parseFixMode(String value, FixMode defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue == null ? FixMode.NONE : defaultValue;
        }
        return EnumParsers.parseIgnoreCase(FixMode.class, value, defaultValue == null ? FixMode.NONE : defaultValue);
    }

    /**
     * guidance 只做结构化清洗，不再根据自然语言内容猜该不该保留。
     */
    List<String> sanitizeGuidanceItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        for (String item : items) {
            String trimmed = blank(item).trim();
            if (trimmed.isBlank()) {
                continue;
            }
            kept.add(trimmed);
        }
        return kept;
    }

    String blank(String value) {
        return value == null ? "" : value;
    }
}
