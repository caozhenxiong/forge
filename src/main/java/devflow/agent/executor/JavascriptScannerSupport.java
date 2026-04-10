package devflow.agent.executor;

/**
 * JavaScript 行为扫描共享的轻量词法辅助。
 *
 * <p>这里只做字符级边界判断和括号匹配，不承载任何完整性语义，
 * 便于不同扫描器复用同一套低层规则。
 */
final class JavascriptScannerSupport {

    String extractPropertyIdentifierBeforeColon(String source, int colonIndex) {
        int cursor = colonIndex - 1;
        while (cursor >= 0 && Character.isWhitespace(source.charAt(cursor))) {
            cursor--;
        }
        if (cursor < 0 || !isIdentifierPart(source.charAt(cursor))) {
            return "";
        }
        int end = cursor + 1;
        while (cursor >= 0 && isIdentifierPart(source.charAt(cursor))) {
            cursor--;
        }
        int start = cursor + 1;
        if (start >= end || !isIdentifierStart(source.charAt(start))) {
            return "";
        }
        return source.substring(start, end);
    }

    int skipWhitespace(String source, int index) {
        int cursor = Math.max(0, index);
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    boolean startsWithArrow(String source, int index) {
        return index + 1 < source.length() && source.charAt(index) == '=' && source.charAt(index + 1) == '>';
    }

    int findMatching(String source, int startIndex, char open, char close) {
        int depth = 0;
        for (int index = startIndex; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == open) {
                depth++;
                continue;
            }
            if (current == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    boolean startsWithKeyword(String source, int startIndex, String keyword) {
        if (source == null || keyword == null || keyword.isBlank()) {
            return false;
        }
        if (startIndex < 0 || startIndex + keyword.length() > source.length()) {
            return false;
        }
        if (!source.regionMatches(true, startIndex, keyword, 0, keyword.length())) {
            return false;
        }
        return isKeywordBoundary(source, startIndex - 1)
                && isKeywordBoundary(source, startIndex + keyword.length());
    }

    boolean isKeywordBoundary(String source, int index) {
        if (index < 0 || index >= source.length()) {
            return true;
        }
        char current = source.charAt(index);
        return !Character.isLetterOrDigit(current) && current != '_' && current != '$';
    }

    boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }
}
