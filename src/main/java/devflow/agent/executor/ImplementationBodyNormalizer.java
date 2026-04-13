package devflow.agent.executor;

/**
 * 实现完整性正文归一化器。
 *
 * <p>负责去掉注释、空白和分号，再把源码片段压成便于 no-op / 未实现
 * 标记判断的规范化文本。这样行为扫描器和占位扫描器都能复用同一份规则。
 */
final class ImplementationBodyNormalizer {

    String normalize(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplateQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inHtmlComment = false;
        boolean escaped = false;
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            char next = index + 1 < body.length() ? body.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }
            if (inHtmlComment) {
                if (current == '-' && next == '-' && index + 2 < body.length() && body.charAt(index + 2) == '>') {
                    inHtmlComment = false;
                    index += 2;
                }
                continue;
            }
            if (inSingleQuote || inDoubleQuote || inTemplateQuote) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if ((inSingleQuote && current == '\'')
                        || (inDoubleQuote && current == '"')
                        || (inTemplateQuote && current == '`')) {
                    inSingleQuote = false;
                    inDoubleQuote = false;
                    inTemplateQuote = false;
                }
                if (!Character.isWhitespace(current) && current != ';') {
                    builder.append(Character.toLowerCase(current));
                }
                continue;
            }

            if (current == '/' && next == '/') {
                inLineComment = true;
                index++;
                continue;
            }
            if (current == '/' && next == '*') {
                inBlockComment = true;
                index++;
                continue;
            }
            if (current == '<' && next == '!' && index + 3 < body.length()
                    && body.charAt(index + 2) == '-' && body.charAt(index + 3) == '-') {
                inHtmlComment = true;
                index += 3;
                continue;
            }
            if (current == '#') {
                inLineComment = true;
                continue;
            }
            if (current == '\'') {
                inSingleQuote = true;
            } else if (current == '"') {
                inDoubleQuote = true;
            } else if (current == '`') {
                inTemplateQuote = true;
            }
            if (Character.isWhitespace(current) || current == ';') {
                continue;
            }
            builder.append(Character.toLowerCase(current));
        }
        return builder.toString().trim();
    }

    boolean isNoOpBody(String body) {
        String normalized = normalize(body);
        if (normalized.isBlank()) {
            return true;
        }
        return normalized.equals("pass")
                || normalized.equals("return")
                || normalized.equals("returnnull")
                || normalized.equals("returnnone")
                || normalized.equals("returnundefined")
                || normalized.equals("yieldbreak")
                || isLoggingOnlyBody(normalized)
                || containsAnyNoOpThrowMarker(normalized);
    }

    boolean isNoOpCaseBody(String body) {
        String normalized = normalize(body);
        return normalized.isBlank()
                || normalized.equals("break")
                || normalized.equals("continue")
                || normalized.equals("return")
                || normalized.equals("returnnull")
                || normalized.equals("returnundefined");
    }

    private boolean containsAnyNoOpThrowMarker(String normalizedBody) {
        for (String marker : ImplementationCompletenessPolicy.notImplementedMarkers()) {
            if (normalizedBody.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLoggingOnlyBody(String normalizedBody) {
        String remaining = normalizedBody == null ? "" : normalizedBody;
        boolean matchedLoggingCall = false;
        while (!remaining.isBlank()) {
            remaining = stripStatementPrefix(remaining);
            String prefix = loggingPrefixAtStart(remaining);
            if (prefix == null) {
                return false;
            }
            int callEnd = findMatchingParenthesis(remaining, prefix.length() - 1);
            if (callEnd < 0) {
                return false;
            }
            matchedLoggingCall = true;
            remaining = remaining.substring(callEnd + 1);
        }
        return matchedLoggingCall;
    }

    private String stripStatementPrefix(String value) {
        String remaining = value == null ? "" : value;
        boolean changed;
        do {
            changed = false;
            if (remaining.startsWith("return")) {
                remaining = remaining.substring("return".length());
                changed = true;
            } else if (remaining.startsWith("await")) {
                remaining = remaining.substring("await".length());
                changed = true;
            } else if (remaining.startsWith("void")) {
                remaining = remaining.substring("void".length());
                changed = true;
            }
        } while (changed);
        return remaining;
    }

    private String loggingPrefixAtStart(String value) {
        for (String prefix : ImplementationCompletenessPolicy.loggingOnlyCallPrefixes()) {
            if (value.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    private int findMatchingParenthesis(String value, int openIndex) {
        if (value == null || openIndex < 0 || openIndex >= value.length() || value.charAt(openIndex) != '(') {
            return -1;
        }
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplateQuote = false;
        boolean escaped = false;
        for (int index = openIndex; index < value.length(); index++) {
            char current = value.charAt(index);
            if (inSingleQuote || inDoubleQuote || inTemplateQuote) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if ((inSingleQuote && current == '\'')
                        || (inDoubleQuote && current == '"')
                        || (inTemplateQuote && current == '`')) {
                    inSingleQuote = false;
                    inDoubleQuote = false;
                    inTemplateQuote = false;
                }
                continue;
            }
            if (current == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (current == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (current == '`') {
                inTemplateQuote = true;
                continue;
            }
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }
}
