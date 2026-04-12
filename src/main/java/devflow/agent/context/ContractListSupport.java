package devflow.agent.context;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.text.TextCanonicalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Contract 列表与 metadata 值解析支持。
 *
 * <p>它统一维护 markdown 列表协议、空值标记和 metadata 标量解析，
 * 避免这些规则继续散在 `ContractExtractor` 内部。
 */
final class ContractListSupport {

    private static final char[] COMMA_DELIMITERS = new char[]{',', '，'};
    private static final char[] LOOSE_LIST_DELIMITERS = new char[]{',', '，', ';', '；', '\n'};

    List<String> collectReferenceItems(String sectionBody) {
        return collectReferenceItems(sectionBody, Integer.MAX_VALUE);
    }

    List<String> collectReferenceItems(String sectionBody, int maxItems) {
        if (sectionBody == null || sectionBody.isBlank()) {
            return List.of();
        }
        Set<String> items = new LinkedHashSet<>();
        collectBulletItems(sectionBody, maxItems <= 0 ? Integer.MAX_VALUE : maxItems, items);
        return List.copyOf(items);
    }

    List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return TextCanonicalizer.splitDelimitedValues(value, COMMA_DELIMITERS).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .filter(item -> !isEmptyMarker(item))
                .toList();
    }

    List<String> parseLooseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return TextCanonicalizer.splitDelimitedValues(value, LOOSE_LIST_DELIMITERS).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .filter(item -> !isEmptyMarker(item))
                .distinct()
                .toList();
    }

    List<String> combineLists(List<String> left, List<String> right) {
        return java.util.stream.Stream.concat(
                        left == null ? java.util.stream.Stream.empty() : left.stream(),
                        right == null ? java.util.stream.Stream.empty() : right.stream()
                )
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .filter(item -> !isEmptyMarker(item))
                .distinct()
                .toList();
    }

    boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = normalize(value);
        if (normalized.equals("true") || normalized.equals("yes")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no")) {
            return false;
        }
        return fallback;
    }

    Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private void collectBulletItems(String sectionBody, int maxItems, Set<String> items) {
        for (String rawLine : TextCanonicalizer.splitLines(sectionBody)) {
            if (items.size() >= maxItems) {
                return;
            }
            String item = extractListItem(rawLine);
            if (item.isBlank()) {
                continue;
            }
            items.add(cleanLine(item));
        }
    }

    private String extractListItem(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return "";
        }
        String trimmed = rawLine.trim();
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            return trimmed.substring(2).trim();
        }
        int orderedBodyStart = orderedListBodyStart(trimmed);
        if (orderedBodyStart > 0) {
            return trimmed.substring(orderedBodyStart).trim();
        }
        return "";
    }

    private int orderedListBodyStart(String value) {
        int index = 0;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        if (index == 0 || index + 1 >= value.length() || value.charAt(index) != '.') {
            return -1;
        }
        int bodyStart = index + 1;
        if (!Character.isWhitespace(value.charAt(bodyStart))) {
            return -1;
        }
        while (bodyStart < value.length() && Character.isWhitespace(value.charAt(bodyStart))) {
            bodyStart++;
        }
        return bodyStart < value.length() ? bodyStart : -1;
    }

    private String cleanLine(String value) {
        if (value == null) {
            return "";
        }
        return TextCanonicalizer.collapseWhitespace(TextCanonicalizer.removeCharacter(value, '`'));
    }

    private boolean isEmptyMarker(String value) {
        if (value == null) {
            return true;
        }
        String normalized = normalize(value);
        return normalized.isBlank()
                || PlaceholderValues.isNoneLiteral(normalized)
                || normalized.equals("none")
                || normalized.equals("无")
                || normalized.equals("n/a");
    }

    private String normalize(String value) {
        return value == null ? "" : TextCanonicalizer.removeWhitespace(value).toLowerCase();
    }
}
