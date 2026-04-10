package devflow.agent.text;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一处理“稳定文本协议”里的基础扫描与归一。
 *
 * <p>这里只做确定性的字符级处理，例如：
 * 1. 行拆分；
 * 2. 空白折叠；
 * 3. 分隔符列表拆分；
 * 4. 句子边界切分。
 *
 * <p>它不负责从自然语言里猜语义，也不负责解释模型 prose。
 */
public final class TextCanonicalizer {

    private TextCanonicalizer() {
    }

    public static List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) != '\n') {
                continue;
            }
            lines.add(trimTrailingCarriageReturn(content.substring(start, index)));
            start = index + 1;
        }
        lines.add(trimTrailingCarriageReturn(content.substring(start)));
        return List.copyOf(lines);
    }

    public static String collapseWhitespace(String value) {
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
            builder.append(current);
            previousWhitespace = false;
        }
        return builder.toString().trim();
    }

    public static String removeWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!Character.isWhitespace(current)) {
                builder.append(current);
            }
        }
        return builder.toString().trim();
    }

    public static String removeCharacter(String value, char target) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != target) {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    public static List<String> splitDelimitedValues(String value, char... delimiters) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (isDelimiter(ch, delimiters)) {
                appendNormalizedPart(parts, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        appendNormalizedPart(parts, current);
        return List.copyOf(parts);
    }

    public static List<String> splitSentences(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            current.append(ch);
            if (!isSentenceBoundary(ch)) {
                continue;
            }
            if (!nextStartsNewSentence(value, index + 1)) {
                continue;
            }
            appendNormalizedPart(sentences, current);
            current.setLength(0);
        }
        appendNormalizedPart(sentences, current);
        return List.copyOf(sentences);
    }

    public static String collapseLineBreaks(String value, String separator) {
        if (value == null || value.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String line : splitLines(value)) {
            String normalized = collapseWhitespace(line);
            if (!normalized.isBlank()) {
                parts.add(normalized);
            }
        }
        return String.join(separator, parts);
    }

    public static String trimTrailingHorizontalWhitespacePerLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        int pendingHorizontalWhitespace = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == ' ' || current == '\t') {
                pendingHorizontalWhitespace++;
                continue;
            }
            if (current == '\n' || current == '\r') {
                pendingHorizontalWhitespace = 0;
                builder.append(current);
                continue;
            }
            while (pendingHorizontalWhitespace-- > 0) {
                builder.append(' ');
            }
            pendingHorizontalWhitespace = 0;
            builder.append(current);
        }
        return builder.toString();
    }

    public static String normalizeHtmlFragment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        boolean previousWasTagClose = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                previousWhitespace = true;
                continue;
            }
            if (current == '<' && previousWasTagClose && !builder.isEmpty() && builder.charAt(builder.length() - 1) == ' ') {
                builder.deleteCharAt(builder.length() - 1);
            }
            if (previousWhitespace && !builder.isEmpty() && builder.charAt(builder.length() - 1) != '>') {
                builder.append(' ');
            }
            builder.append(current);
            previousWasTagClose = current == '>';
            previousWhitespace = false;
        }
        return builder.toString().trim();
    }

    private static boolean isDelimiter(char value, char... delimiters) {
        for (char delimiter : delimiters) {
            if (value == delimiter) {
                return true;
            }
        }
        return false;
    }

    private static void appendNormalizedPart(List<String> target, StringBuilder current) {
        String normalized = collapseWhitespace(current.toString());
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }

    private static boolean isSentenceBoundary(char value) {
        return value == '.'
                || value == '!'
                || value == '?'
                || value == ';'
                || value == '。'
                || value == '！'
                || value == '？'
                || value == '；';
    }

    private static boolean nextStartsNewSentence(String value, int startIndex) {
        for (int index = startIndex; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                continue;
            }
            return true;
        }
        return true;
    }

    private static String trimTrailingCarriageReturn(String value) {
        if (value.endsWith("\r")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
