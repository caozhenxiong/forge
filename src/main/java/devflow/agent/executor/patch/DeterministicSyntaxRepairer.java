package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 只覆盖局部可确定修复的语法/结构错误。
 *
 * <p>当前只处理两类稳定问题：
 * 1. EOF 缺失闭合符；
 * 2. 同名函数/类声明被重复包了一层 wrapper。
 *
 * <p>这层仍然不猜业务逻辑，也不尝试重写实现。
 */
public final class DeterministicSyntaxRepairer {

    public String repair(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String structuralRepair = repairDuplicatedDeclarationWrapper(content);
        if (!structuralRepair.equals(content)) {
            return structuralRepair;
        }
        Deque<Character> stack = new ArrayDeque<>();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            char next = index + 1 < content.length() ? content.charAt(index + 1) : '\0';
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
            if (inSingleQuote || inDoubleQuote || inTemplate) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (inSingleQuote && current == '\'') {
                    inSingleQuote = false;
                } else if (inDoubleQuote && current == '"') {
                    inDoubleQuote = false;
                } else if (inTemplate && current == '`') {
                    inTemplate = false;
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
            if (current == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (current == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (current == '`') {
                inTemplate = true;
                continue;
            }
            if (current == '{' || current == '[' || current == '(') {
                stack.push(current);
                continue;
            }
            if (current == '}' || current == ']' || current == ')') {
                if (stack.isEmpty() || !matches(stack.peek(), current)) {
                    return content;
                }
                stack.pop();
            }
        }
        if (inSingleQuote || inDoubleQuote || inTemplate || inBlockComment) {
            return content;
        }
        if (stack.isEmpty()) {
            return content;
        }
        StringBuilder builder = new StringBuilder(content.stripTrailing());
        builder.append('\n');
        while (!stack.isEmpty()) {
            builder.append(closeFor(stack.pop()));
        }
        return builder.toString();
    }

    private String repairDuplicatedDeclarationWrapper(String content) {
        String repaired = content;
        for (int iteration = 0; iteration < 16; iteration++) {
            String functionRepaired = unwrapDuplicatedDeclaration(repaired, "function");
            if (!functionRepaired.equals(repaired)) {
                repaired = functionRepaired;
                continue;
            }
            String classRepaired = unwrapDuplicatedDeclaration(repaired, "class");
            if (!classRepaired.equals(repaired)) {
                repaired = classRepaired;
                continue;
            }
            break;
        }
        return repaired;
    }

    private String unwrapDuplicatedDeclaration(String source, String keyword) {
        int searchIndex = 0;
        while (searchIndex >= 0 && searchIndex < source.length()) {
            int declarationStart = source.indexOf(keyword, searchIndex);
            if (declarationStart < 0) {
                return source;
            }
            String name = readIdentifierAfterKeyword(source, declarationStart + keyword.length());
            if (name == null) {
                searchIndex = declarationStart + keyword.length();
                continue;
            }
            int outerBodyStart = findBodyStart(source, declarationStart + keyword.length());
            if (outerBodyStart < 0) {
                searchIndex = declarationStart + keyword.length();
                continue;
            }
            int nestedStart = skipIgnorable(source, outerBodyStart + 1);
            if (!startsWithDeclaration(source, nestedStart, keyword, name)) {
                searchIndex = outerBodyStart + 1;
                continue;
            }
            int innerBodyStart = findBodyStart(source, nestedStart + keyword.length());
            if (innerBodyStart < 0) {
                searchIndex = outerBodyStart + 1;
                continue;
            }
            int innerBodyEnd = findMatchingBrace(source, innerBodyStart);
            int outerBodyEnd = findMatchingBrace(source, outerBodyStart);
            if (innerBodyEnd < 0 || outerBodyEnd < 0 || innerBodyEnd >= outerBodyEnd) {
                searchIndex = outerBodyStart + 1;
                continue;
            }
            if (!containsOnlyIgnorable(source, innerBodyEnd + 1, outerBodyEnd)) {
                searchIndex = outerBodyStart + 1;
                continue;
            }
            return source.substring(0, declarationStart)
                    + source.substring(nestedStart, innerBodyEnd + 1)
                    + source.substring(outerBodyEnd + 1);
        }
        return source;
    }

    private boolean matches(char open, char close) {
        return (open == '{' && close == '}')
                || (open == '[' && close == ']')
                || (open == '(' && close == ')');
    }

    private char closeFor(char open) {
        return switch (open) {
            case '{' -> '}';
            case '[' -> ']';
            case '(' -> ')';
            default -> open;
        };
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

    private int findMatchingBrace(String source, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= source.length() || source.charAt(openBraceIndex) != '{') {
            return -1;
        }
        Deque<Character> stack = new ArrayDeque<>();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;
        for (int index = openBraceIndex; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
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
            if (inSingleQuote || inDoubleQuote || inTemplate) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (inSingleQuote && current == '\'') {
                    inSingleQuote = false;
                } else if (inDoubleQuote && current == '"') {
                    inDoubleQuote = false;
                } else if (inTemplate && current == '`') {
                    inTemplate = false;
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
            if (current == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (current == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (current == '`') {
                inTemplate = true;
                continue;
            }
            if (current == '{') {
                stack.push(current);
                continue;
            }
            if (current == '}') {
                if (stack.isEmpty()) {
                    return -1;
                }
                stack.pop();
                if (stack.isEmpty()) {
                    return index;
                }
            }
        }
        return -1;
    }

    private boolean containsOnlyIgnorable(String source, int startInclusive, int endExclusive) {
        if (startInclusive >= endExclusive) {
            return true;
        }
        return skipIgnorable(source, startInclusive) >= endExclusive;
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
