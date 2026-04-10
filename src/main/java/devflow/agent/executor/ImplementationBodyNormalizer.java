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
}
