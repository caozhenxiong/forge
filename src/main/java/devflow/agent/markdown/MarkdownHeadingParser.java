package devflow.agent.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Markdown 标题解析器。
 *
 * <p>负责固定协议里的二级/三级标题、编号路径和标题归一化，
 * 避免章节扫描器继续同时承担扫描与标题语法解析。
 */
final class MarkdownHeadingParser {

    boolean isSecondLevelHeading(String line) {
        return parseSecondLevelHeading(line) != null;
    }

    MarkdownSectionScanner.Heading parseSecondLevelHeading(String line) {
        return parseHeading(line, 2);
    }

    MarkdownSectionScanner.Heading parseThirdLevelHeading(String line) {
        return parseHeading(line, 3);
    }

    boolean isHeadingLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String trimmed = line.trim();
        int markerCount = 0;
        while (markerCount < trimmed.length() && trimmed.charAt(markerCount) == '#') {
            markerCount++;
        }
        return markerCount > 0
                && markerCount < trimmed.length()
                && markerCount <= 6
                && Character.isWhitespace(trimmed.charAt(markerCount));
    }

    String normalizeHeadingTitle(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                if (!previousWhitespace && !builder.isEmpty()) {
                    builder.append(' ');
                }
                previousWhitespace = true;
                continue;
            }
            builder.append(Character.toLowerCase(current));
            previousWhitespace = false;
        }
        return builder.toString().trim();
    }

    MarkdownSectionScanner.NumberedHeading parseNumberedHeading(String line, int level) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String trimmed = line.trim();
        String marker = "#".repeat(level) + " ";
        if (!trimmed.startsWith(marker) || trimmed.startsWith("#".repeat(level + 1))) {
            return null;
        }
        String body = trimmed.substring(marker.length()).trim();
        if (body.isEmpty()) {
            return null;
        }
        int prefixEnd = findNumberPathEnd(body);
        if (prefixEnd <= 0 || prefixEnd >= body.length()) {
            return new MarkdownSectionScanner.NumberedHeading(List.of(), body);
        }
        String prefix = body.substring(0, prefixEnd).trim();
        List<Integer> numberPath = parseNumberPath(prefix);
        if (numberPath.isEmpty()) {
            return new MarkdownSectionScanner.NumberedHeading(List.of(), body);
        }
        String title = body.substring(prefixEnd).trim();
        return new MarkdownSectionScanner.NumberedHeading(numberPath, title);
    }

    private MarkdownSectionScanner.Heading parseHeading(String line, int level) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String trimmed = line.trim();
        String marker = "#".repeat(level) + " ";
        if (!trimmed.startsWith(marker) || trimmed.startsWith("#".repeat(level + 1))) {
            return null;
        }
        String body = trimmed.substring(marker.length()).trim();
        if (body.isEmpty()) {
            return null;
        }
        int cursor = 0;
        while (cursor < body.length() && Character.isDigit(body.charAt(cursor))) {
            cursor++;
        }
        if (cursor > 0 && cursor + 1 < body.length() && body.charAt(cursor) == '.' && Character.isWhitespace(body.charAt(cursor + 1))) {
            int number = Integer.parseInt(body.substring(0, cursor));
            String title = body.substring(cursor + 1).trim();
            return new MarkdownSectionScanner.Heading(number, title);
        }
        return new MarkdownSectionScanner.Heading(-1, body);
    }

    private int findNumberPathEnd(String value) {
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (Character.isDigit(current) || current == '.') {
                index++;
                continue;
            }
            if (Character.isWhitespace(current)) {
                return index;
            }
            return -1;
        }
        return -1;
    }

    private List<Integer> parseNumberPath(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Integer> numbers = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.isDigit(ch)) {
                current.append(ch);
                continue;
            }
            if (ch == '.') {
                if (!flushNumber(numbers, current)) {
                    return List.of();
                }
                continue;
            }
            return List.of();
        }
        if (!flushNumber(numbers, current)) {
            return List.of();
        }
        return Collections.unmodifiableList(numbers);
    }

    private boolean flushNumber(List<Integer> target, StringBuilder current) {
        if (current.isEmpty()) {
            return false;
        }
        target.add(Integer.parseInt(current.toString()));
        current.setLength(0);
        return true;
    }
}
