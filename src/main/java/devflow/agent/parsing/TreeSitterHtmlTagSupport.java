package devflow.agent.parsing;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML 标签文本解析支持。
 *
 * <p>这里专门处理 start/end tag 的轻量字符串解析，
 * 避免结构扫描器继续混入 tagName/attribute/class token 的细节。
 */
final class TreeSitterHtmlTagSupport {

    String extractTagName(String tagSource) {
        if (tagSource == null || tagSource.isBlank()) {
            return "";
        }
        int index = 0;
        while (index < tagSource.length() && Character.isWhitespace(tagSource.charAt(index))) {
            index++;
        }
        if (index >= tagSource.length() || tagSource.charAt(index) != '<') {
            return "";
        }
        index++;
        while (index < tagSource.length() && (Character.isWhitespace(tagSource.charAt(index))
                || tagSource.charAt(index) == '/'
                || tagSource.charAt(index) == '!')) {
            index++;
        }
        int start = index;
        while (index < tagSource.length() && isTagNamePart(tagSource.charAt(index))) {
            index++;
        }
        return start < index ? tagSource.substring(start, index) : "";
    }

    String extractAttribute(String tagSource, String attributeName) {
        if (tagSource == null || tagSource.isBlank() || attributeName == null || attributeName.isBlank()) {
            return "";
        }
        for (int index = 0; index < tagSource.length(); index++) {
            if (!matchesAsciiWordIgnoreCase(tagSource, index, attributeName)) {
                continue;
            }
            if (!isAttributeBoundary(tagSource, index - 1)
                    || !isAttributeBoundary(tagSource, index + attributeName.length())) {
                continue;
            }
            int cursor = index + attributeName.length();
            while (cursor < tagSource.length() && Character.isWhitespace(tagSource.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= tagSource.length() || tagSource.charAt(cursor) != '=') {
                continue;
            }
            cursor++;
            while (cursor < tagSource.length() && Character.isWhitespace(tagSource.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= tagSource.length()) {
                return "";
            }
            char delimiter = tagSource.charAt(cursor);
            if (delimiter == '"' || delimiter == '\'') {
                int valueStart = cursor + 1;
                int valueEnd = tagSource.indexOf(delimiter, valueStart);
                return valueEnd > valueStart ? tagSource.substring(valueStart, valueEnd).trim() : "";
            }
            int valueStart = cursor;
            while (cursor < tagSource.length() && !Character.isWhitespace(tagSource.charAt(cursor))
                    && tagSource.charAt(cursor) != '>') {
                cursor++;
            }
            return tagSource.substring(valueStart, cursor).trim();
        }
        return "";
    }

    List<String> splitWhitespaceTokens(String value) {
        List<String> tokens = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);
            if (Character.isWhitespace(currentChar)) {
                appendToken(tokens, current);
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        appendToken(tokens, current);
        return tokens;
    }

    private boolean isTagNamePart(char value) {
        return Character.isLetterOrDigit(value) || value == ':' || value == '-';
    }

    private boolean matchesAsciiWordIgnoreCase(String value, int offset, String word) {
        if (offset < 0 || offset + word.length() > value.length()) {
            return false;
        }
        for (int index = 0; index < word.length(); index++) {
            if (Character.toLowerCase(value.charAt(offset + index)) != Character.toLowerCase(word.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean isAttributeBoundary(String value, int index) {
        if (index < 0 || index >= value.length()) {
            return true;
        }
        char current = value.charAt(index);
        return Character.isWhitespace(current) || current == '<' || current == '>' || current == '/' || current == '=';
    }

    private void appendToken(List<String> target, StringBuilder current) {
        if (!current.isEmpty()) {
            target.add(current.toString());
        }
    }
}
