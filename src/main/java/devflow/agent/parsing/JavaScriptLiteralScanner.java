package devflow.agent.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 只扫描稳定的 JavaScript 字面量调用形态。
 *
 * <p>当前仅覆盖运行时接线检查需要的固定结构：
 * 1. `getElementById("...")`
 * 2. `getElementsByClassName("...")`
 * 3. `querySelector("#...") / querySelectorAll(". ...")`
 * 4. `import("...")`
 * 5. `from "..." / from '...'`
 *
 * <p>它不试图理解完整 JavaScript 语法，也不承担语义推断。
 */
public final class JavaScriptLiteralScanner {

    private JavaScriptLiteralScanner() {
    }

    public static List<String> extractCallStringArguments(String source, String callName) {
        if (source == null || source.isBlank() || callName == null || callName.isBlank()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        int cursor = 0;
        while (cursor >= 0 && cursor < source.length()) {
            int matchIndex = source.indexOf(callName, cursor);
            if (matchIndex < 0) {
                break;
            }
            if (!isTokenBoundary(source, matchIndex - 1)
                    || !isTokenBoundary(source, matchIndex + callName.length())) {
                cursor = matchIndex + callName.length();
                continue;
            }
            int parenIndex = skipWhitespace(source, matchIndex + callName.length());
            if (parenIndex >= source.length() || source.charAt(parenIndex) != '(') {
                cursor = matchIndex + callName.length();
                continue;
            }
            int valueStart = skipWhitespace(source, parenIndex + 1);
            String literal = readQuotedLiteral(source, valueStart);
            if (literal != null) {
                results.add(literal);
            }
            cursor = parenIndex + 1;
        }
        return List.copyOf(results);
    }

    public static List<String> extractImportSpecifiers(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        List<String> specifiers = new ArrayList<>();
        specifiers.addAll(extractCallStringArguments(source, "import"));

        int cursor = 0;
        while (cursor >= 0 && cursor < source.length()) {
            int matchIndex = indexOfIgnoreCase(source, "from", cursor);
            if (matchIndex < 0) {
                break;
            }
            if (!isTokenBoundary(source, matchIndex - 1)
                    || !isTokenBoundary(source, matchIndex + "from".length())) {
                cursor = matchIndex + "from".length();
                continue;
            }
            int literalStart = skipWhitespace(source, matchIndex + "from".length());
            String literal = readQuotedLiteral(source, literalStart);
            if (literal != null) {
                specifiers.add(literal);
            }
            cursor = matchIndex + "from".length();
        }
        return List.copyOf(specifiers);
    }

    private static int indexOfIgnoreCase(String source, String token, int fromIndex) {
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        return normalizedSource.indexOf(normalizedToken, fromIndex);
    }

    private static int skipWhitespace(String source, int index) {
        int cursor = Math.max(0, index);
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static String readQuotedLiteral(String source, int startIndex) {
        if (startIndex < 0 || startIndex >= source.length()) {
            return null;
        }
        char quote = source.charAt(startIndex);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int index = startIndex + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (escaped) {
                builder.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == quote) {
                return builder.toString();
            }
            builder.append(current);
        }
        return null;
    }

    private static boolean isTokenBoundary(String source, int index) {
        if (index < 0 || index >= source.length()) {
            return true;
        }
        char current = source.charAt(index);
        return !Character.isLetterOrDigit(current) && current != '_' && current != '$';
    }
}
