package devflow.agent.executor;

/**
 * 本地确定性 JSON 载荷修复器。
 *
 * <p>这里只做“无需重新理解任务也能安全修”的协议修复：
 * 1. 抽取平衡的 JSON 对象；
 * 2. 规范引号；
 * 3. 移除尾逗号；
 * 4. 把字符串中的原始控制字符转成转义形式。
 */
final class DeterministicJsonPayloadRepairer {

    String repair(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return rawPayload;
        }
        String candidate = rawPayload.strip();
        String extracted = extractBalancedJsonObject(candidate);
        if (extracted != null) {
            candidate = extracted;
        }
        candidate = normalizeQuotes(candidate);
        candidate = escapeControlCharactersInStrings(candidate);
        candidate = removeTrailingCommas(candidate);
        return candidate.strip();
    }

    private String extractBalancedJsonObject(String content) {
        int start = content.indexOf('{');
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '{') {
                depth++;
                continue;
            }
            if (current == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, index + 1);
                }
            }
        }
        return content.substring(start);
    }

    private String normalizeQuotes(String content) {
        return content
                .replace('“', '"')
                .replace('”', '"')
                .replace('‘', '\'')
                .replace('’', '\'');
    }

    private String escapeControlCharactersInStrings(String content) {
        StringBuilder builder = new StringBuilder(content.length() + 32);
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                if (escaped) {
                    builder.append(current);
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    builder.append(current);
                    escaped = true;
                    continue;
                }
                if (current == '"') {
                    builder.append(current);
                    inString = false;
                    continue;
                }
                if (current == '\n') {
                    builder.append("\\n");
                    continue;
                }
                if (current == '\r') {
                    builder.append("\\r");
                    continue;
                }
                if (current == '\t') {
                    builder.append("\\t");
                    continue;
                }
                builder.append(current);
                continue;
            }
            if (current == '"') {
                inString = true;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private String removeTrailingCommas(String content) {
        StringBuilder builder = new StringBuilder(content.length());
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                builder.append(current);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                builder.append(current);
                continue;
            }
            if (current == ',') {
                int nextIndex = index + 1;
                while (nextIndex < content.length() && Character.isWhitespace(content.charAt(nextIndex))) {
                    nextIndex++;
                }
                if (nextIndex < content.length()) {
                    char next = content.charAt(nextIndex);
                    if (next == '}' || next == ']') {
                        continue;
                    }
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }
}
