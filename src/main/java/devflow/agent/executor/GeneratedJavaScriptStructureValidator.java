package devflow.agent.executor;

/**
 * 负责 JavaScript 的结构性反模式校验。
 *
 * <p>这层不判断业务语义，只拦“repair 后明显拼坏了宿主脚本结构”这类通用问题，
 * 例如同名声明被重复包了一层 wrapper。
 */
final class GeneratedJavaScriptStructureValidator {

    GeneratedContentValidationFailure validate(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String duplicatedFunction = findDuplicatedFunctionWrapper(content);
        if (duplicatedFunction != null) {
            return new GeneratedContentValidationFailure(
                    GeneratedContentValidationCode.JAVASCRIPT_STRUCTURE_INVALID,
                    "JavaScript 结构异常：同名函数包装被重复嵌套 -> " + duplicatedFunction
            );
        }
        String duplicatedClass = findDuplicatedClassWrapper(content);
        if (duplicatedClass != null) {
            return new GeneratedContentValidationFailure(
                    GeneratedContentValidationCode.JAVASCRIPT_STRUCTURE_INVALID,
                    "JavaScript 结构异常：同名类包装被重复嵌套 -> " + duplicatedClass
            );
        }
        return null;
    }

    private String findDuplicatedFunctionWrapper(String source) {
        int index = 0;
        while (index >= 0 && index < source.length()) {
            index = source.indexOf("function", index);
            if (index < 0) {
                return null;
            }
            String name = readIdentifierAfterKeyword(source, index + "function".length());
            if (name == null) {
                index += "function".length();
                continue;
            }
            int bodyStart = findBodyStart(source, index + "function".length());
            if (bodyStart < 0) {
                index += "function".length();
                continue;
            }
            int nestedStart = skipIgnorable(source, bodyStart + 1);
            if (startsWithDeclaration(source, nestedStart, "function", name)) {
                return name;
            }
            index = bodyStart + 1;
        }
        return null;
    }

    private String findDuplicatedClassWrapper(String source) {
        int index = 0;
        while (index >= 0 && index < source.length()) {
            index = source.indexOf("class", index);
            if (index < 0) {
                return null;
            }
            String name = readIdentifierAfterKeyword(source, index + "class".length());
            if (name == null) {
                index += "class".length();
                continue;
            }
            int bodyStart = source.indexOf('{', index + "class".length());
            if (bodyStart < 0) {
                index += "class".length();
                continue;
            }
            int nestedStart = skipIgnorable(source, bodyStart + 1);
            if (startsWithDeclaration(source, nestedStart, "class", name)) {
                return name;
            }
            index = bodyStart + 1;
        }
        return null;
    }

    private boolean startsWithDeclaration(String source, int index, String keyword, String name) {
        if (index < 0 || index >= source.length()) {
            return false;
        }
        if (!source.startsWith(keyword, index)) {
            return false;
        }
        String nestedName = readIdentifierAfterKeyword(source, index + keyword.length());
        return nestedName != null && nestedName.equals(name);
    }

    private int findBodyStart(String source, int index) {
        int searchIndex = index;
        while (searchIndex >= 0 && searchIndex < source.length()) {
            char current = source.charAt(searchIndex);
            if (current == '{') {
                return searchIndex;
            }
            searchIndex++;
        }
        return -1;
    }

    private String readIdentifierAfterKeyword(String source, int index) {
        int start = skipIgnorable(source, index);
        if (start < 0 || start >= source.length()) {
            return null;
        }
        int end = start;
        while (end < source.length()) {
            char current = source.charAt(end);
            if (!Character.isLetterOrDigit(current) && current != '_' && current != '$') {
                break;
            }
            end++;
        }
        if (end == start) {
            return null;
        }
        return source.substring(start, end);
    }

    private int skipIgnorable(String source, int index) {
        int current = index;
        while (current >= 0 && current < source.length()) {
            char value = source.charAt(current);
            if (!Character.isWhitespace(value)) {
                return current;
            }
            current++;
        }
        return current;
    }
}
