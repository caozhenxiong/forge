package devflow.agent.executor;

import devflow.agent.editing.CodePatchContentResolver;
import devflow.agent.editing.CodePreciseAction;
import devflow.agent.editing.CodePreciseOperation;
import devflow.agent.editing.CodePrecisePatch;
import devflow.agent.editing.PreciseEditException;
import devflow.agent.editing.PreciseEditFailureReason;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * 对代码 patch 单元施加更严格的协议约束。
 *
 * <p>这层专门处理“单符号 body-only 单元”：
 * 当当前 edit unit 已经缩到单个现有符号时，模型不应再返回整段函数/类声明，
 * 否则 body replace 会把声明重复嵌进现有符号体内，形成重复 wrapper。
 */
final class CodePatchUnitContractValidator {

    CodePrecisePatch repairRepeatedDeclarationPayload(EditUnit unit, CodePrecisePatch patch) {
        if (!isStrictBodyOnlyUnit(unit) || patch == null || patch.operations() == null) {
            return patch;
        }
        List<CodePreciseOperation> operations = patch.operations().stream()
                .filter(operation -> operation != null && operation.action() != null)
                .toList();
        if (operations.size() != 1) {
            return patch;
        }
        CodePreciseOperation operation = operations.getFirst();
        String targetSymbol = unit.allowedSymbols().isEmpty() ? "" : unit.allowedSymbols().getFirst();
        if (!targetSymbol.equals(operation.targetSymbol())) {
            return patch;
        }
        if (operation.action() == CodePreciseAction.REPLACE_SYMBOL) {
            String fullSymbolContent = CodePatchContentResolver.resolve(operation);
            String repairedBody = stripWholeSymbolWrapper(fullSymbolContent, targetSymbol, operation.targetKind());
            if (!repairedBody.equals(fullSymbolContent) && !redeclaresTargetSymbol(repairedBody, targetSymbol)) {
                return new CodePrecisePatch(List.of(
                        new CodePreciseOperation(
                                CodePreciseAction.REPLACE_SYMBOL_BODY,
                                operation.targetSymbol(),
                                operation.targetKind(),
                                null,
                                splitLines(repairedBody)
                        )
                ));
            }
            return patch;
        }
        if (operation.action() != CodePreciseAction.REPLACE_SYMBOL_BODY) {
            return patch;
        }
        String bodyContent = CodePatchContentResolver.resolve(operation);
        if (!redeclaresTargetSymbol(bodyContent, targetSymbol)) {
            return patch;
        }
        String repairedBody = stripRepeatedDeclarationWrappers(bodyContent, targetSymbol);
        if (repairedBody.equals(bodyContent) || redeclaresTargetSymbol(repairedBody, targetSymbol)) {
            return patch;
        }
        return new CodePrecisePatch(List.of(
                new CodePreciseOperation(
                        operation.action(),
                        operation.targetSymbol(),
                        operation.targetKind(),
                        null,
                        splitLines(repairedBody)
                )
        ));
    }

    void validate(Path relativePath, EditUnit unit, CodePrecisePatch patch) {
        if (!isStrictBodyOnlyUnit(unit) || patch == null || patch.operations() == null) {
            return;
        }
        List<CodePreciseOperation> operations = patch.operations().stream()
                .filter(operation -> operation != null && operation.action() != null)
                .toList();
        if (operations.size() != 1) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Strict single-symbol code unit %s for %s must contain exactly one operation."
                            .formatted(unit.label(), relativePath)
            );
        }
        CodePreciseOperation operation = operations.getFirst();
        if (operation.action() != CodePreciseAction.REPLACE_SYMBOL_BODY) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Strict single-symbol code unit %s for %s must use REPLACE_SYMBOL_BODY."
                            .formatted(unit.label(), relativePath)
            );
        }
        String targetSymbol = unit.allowedSymbols().getFirst();
        if (!targetSymbol.equals(operation.targetSymbol())) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Strict single-symbol code unit %s for %s must only target %s."
                            .formatted(unit.label(), relativePath, targetSymbol)
            );
        }
        String bodyContent = CodePatchContentResolver.resolve(operation);
        if (redeclaresTargetSymbol(bodyContent, targetSymbol)) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Strict single-symbol code unit %s for %s repeated declaration for %s inside body payload."
                            .formatted(unit.label(), relativePath, targetSymbol)
            );
        }
    }

    private boolean isStrictBodyOnlyUnit(EditUnit unit) {
        return unit != null && unit.restrictsSymbols() && !unit.splittable() && unit.allowedSymbols().size() == 1;
    }

    private boolean redeclaresTargetSymbol(String bodyContent, String targetSymbol) {
        if (bodyContent == null || bodyContent.isBlank() || targetSymbol == null || targetSymbol.isBlank()) {
            return false;
        }
        String normalizedContent = bodyContent.stripLeading();
        String normalizedTarget = targetSymbol.trim().toLowerCase(Locale.ROOT);
        return startsWithNamedDeclaration(normalizedContent, "function", normalizedTarget)
                || startsWithNamedDeclaration(normalizedContent, "class", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "const", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "let", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "var", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "export const", normalizedTarget)
                || startsWithNamedDeclaration(normalizedContent, "export function", normalizedTarget)
                || startsWithNamedDeclaration(normalizedContent, "export class", normalizedTarget);
    }

    private boolean startsWithNamedDeclaration(String content, String keyword, String targetSymbol) {
        if (!startsWithKeyword(content, keyword)) {
            return false;
        }
        String remainder = content.substring(keyword.length()).stripLeading();
        return readIdentifier(remainder).equals(targetSymbol);
    }

    private boolean startsWithNamedVariableDeclaration(String content, String keyword, String targetSymbol) {
        if (!startsWithKeyword(content, keyword)) {
            return false;
        }
        String remainder = content.substring(keyword.length()).stripLeading();
        return readIdentifier(remainder).equals(targetSymbol);
    }

    private boolean startsWithKeyword(String content, String keyword) {
        if (!content.regionMatches(true, 0, keyword, 0, keyword.length())) {
            return false;
        }
        if (content.length() == keyword.length()) {
            return true;
        }
        char boundary = content.charAt(keyword.length());
        return Character.isWhitespace(boundary);
    }

    private String readIdentifier(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int end = 0;
        while (end < content.length()) {
            char current = content.charAt(end);
            if (!Character.isLetterOrDigit(current) && current != '_' && current != '$') {
                break;
            }
            end++;
        }
        return end == 0 ? "" : content.substring(0, end).toLowerCase(Locale.ROOT);
    }

    private String stripNamedDeclarationWrapper(String content, String targetSymbol) {
        int bodyStart = findDeclarationBodyStart(content, targetSymbol);
        if (bodyStart < 0) {
            return content;
        }
        int bodyEnd = findMatchingBrace(content, bodyStart);
        if (bodyEnd < 0 || !containsOnlyIgnorable(content, bodyEnd + 1, content.length())) {
            return content;
        }
        return stripOuterBlankLines(content.substring(bodyStart + 1, bodyEnd));
    }

    private int findDeclarationBodyStart(String content, String targetSymbol) {
        String originalContent = content == null ? "" : content;
        String normalizedContent = originalContent.stripLeading();
        int leadingOffset = originalContent.length() - normalizedContent.length();
        String normalizedTarget = targetSymbol == null ? "" : targetSymbol.trim().toLowerCase(Locale.ROOT);
        if (startsWithNamedDeclaration(normalizedContent, "function", normalizedTarget)
                || startsWithNamedDeclaration(normalizedContent, "class", normalizedTarget)
                || startsWithNamedDeclaration(normalizedContent, "export function", normalizedTarget)
                || startsWithNamedDeclaration(normalizedContent, "export class", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "const", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "let", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "var", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "export const", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "export let", normalizedTarget)
                || startsWithNamedVariableDeclaration(normalizedContent, "export var", normalizedTarget)) {
            int bodyStart = normalizedContent.indexOf('{');
            return bodyStart < 0 ? -1 : leadingOffset + bodyStart;
        }
        return -1;
    }

    private int findMatchingBrace(String content, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= content.length() || content.charAt(openBraceIndex) != '{') {
            return -1;
        }
        Deque<Character> stack = new ArrayDeque<>();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;
        for (int index = openBraceIndex; index < content.length(); index++) {
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

    private boolean containsOnlyIgnorable(String content, int startInclusive, int endExclusive) {
        if (startInclusive >= endExclusive) {
            return true;
        }
        String trailing = content.substring(startInclusive, endExclusive).strip();
        return trailing.isEmpty() || trailing.equals(";");
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(content.split("\\R", -1));
    }

    private String stripOuterBlankLines(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        List<String> lines = Arrays.asList(content.split("\\R", -1));
        int start = 0;
        int end = lines.size();
        while (start < end && lines.get(start).isBlank()) {
            start++;
        }
        while (end > start && lines.get(end - 1).isBlank()) {
            end--;
        }
        return String.join("\n", lines.subList(start, end));
    }

    private String stripRepeatedDeclarationWrappers(String content, String targetSymbol) {
        String current = content;
        for (int depth = 0; depth < 4 && redeclaresTargetSymbol(current, targetSymbol); depth++) {
            String stripped = stripNamedDeclarationWrapper(current, targetSymbol);
            if (stripped.equals(current)) {
                break;
            }
            current = stripped;
        }
        return current;
    }

    private String stripWholeSymbolWrapper(String content, String targetSymbol, String targetKind) {
        int bodyStart = findStructuredSymbolBodyStart(content, targetSymbol, targetKind);
        if (bodyStart < 0) {
            return content;
        }
        int bodyEnd = findMatchingBrace(content, bodyStart);
        if (bodyEnd < 0 || !containsOnlyIgnorable(content, bodyEnd + 1, content.length())) {
            return content;
        }
        return stripOuterBlankLines(content.substring(bodyStart + 1, bodyEnd));
    }

    private int findStructuredSymbolBodyStart(String content, String targetSymbol, String targetKind) {
        String originalContent = content == null ? "" : content;
        String normalizedContent = originalContent.stripLeading();
        int leadingOffset = originalContent.length() - normalizedContent.length();
        int bodyStart = normalizedContent.indexOf('{');
        if (bodyStart < 0) {
            return -1;
        }
        String header = normalizedContent.substring(0, bodyStart).strip();
        String normalizedHeader = header.toLowerCase(Locale.ROOT);
        String normalizedTarget = targetSymbol == null ? "" : targetSymbol.trim().toLowerCase(Locale.ROOT);
        String normalizedKind = targetKind == null ? "" : targetKind.trim().toLowerCase(Locale.ROOT);
        boolean matches;
        if ("class".equals(normalizedKind)) {
            matches = normalizedHeader.contains("class") && normalizedHeader.contains(normalizedTarget);
        } else if ("constructor".equals(normalizedKind)) {
            matches = normalizedHeader.contains("constructor(") || normalizedHeader.contains(normalizedTarget + "(");
        } else {
            matches = normalizedHeader.contains(normalizedTarget + "(");
        }
        return matches ? leadingOffset + bodyStart : -1;
    }
}
