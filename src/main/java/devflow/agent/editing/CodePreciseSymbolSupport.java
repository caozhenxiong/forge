package devflow.agent.editing;

import devflow.agent.parsing.CodeStructureSnapshot;
import devflow.agent.parsing.CodeSymbol;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 精确编辑里的符号探测与目标归一化支撑。
 *
 * <p>这里统一负责：
 * 1. 读取 tree-sitter 结构快照；
 * 2. 暴露稳定的符号摘要；
 * 3. 把模型给出的 target kind 对齐到源码里真实存在的符号类型。
 */
final class CodePreciseSymbolSupport {

    private final TreeSitterSupport treeSitterSupport;

    CodePreciseSymbolSupport(TreeSitterSupport treeSitterSupport) {
        this.treeSitterSupport = treeSitterSupport;
    }

    CodeStructureSnapshot inspect(Path relativePath, String source) {
        return treeSitterSupport.inspectCodeStructure(relativePath, source == null ? "" : source);
    }

    boolean supportsPreciseEditing(Path relativePath, String source) {
        return inspect(relativePath, source).supportsPreciseEditing();
    }

    boolean supportsAppendOnlyEditing(Path relativePath, String source) {
        CodeStructureSnapshot snapshot = inspect(relativePath, source);
        return snapshot.parseSummary().supported() && snapshot.parseSummary().valid();
    }

    String describeSymbols(Path relativePath, String source) {
        CodeStructureSnapshot snapshot = inspect(relativePath, source);
        StringBuilder builder = new StringBuilder();
        builder.append("- language: ").append(snapshot.language()).append('\n');
        builder.append("- supportsPreciseEditing: ").append(snapshot.supportsPreciseEditing()).append('\n');
        int limit = Math.min(snapshot.symbols().size(), 12);
        for (int index = 0; index < limit; index++) {
            CodeSymbol symbol = snapshot.symbols().get(index);
            builder.append("- symbol: ")
                    .append(symbol.kind())
                    .append(" ")
                    .append(symbol.name())
                    .append(" insertion=")
                    .append(symbol.supportsInsertion())
                    .append('\n');
        }
        return builder.toString().strip();
    }

    List<String> listSymbolNames(Path relativePath, String source) {
        return inspect(relativePath, source).symbols().stream()
                .map(CodeSymbol::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    List<String> listInsertableSymbolNames(Path relativePath, String source) {
        return inspect(relativePath, source).symbols().stream()
                .filter(CodeSymbol::supportsInsertion)
                .map(CodeSymbol::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    boolean hasInsertableSymbols(Path relativePath, String source) {
        return !listInsertableSymbolNames(relativePath, source).isEmpty();
    }

    CodePrecisePatch normalizePatchTargets(Path relativePath, String source, CodePrecisePatch patch) {
        if (patch == null || !patch.hasAnyOperation()) {
            return patch;
        }
        CodeStructureSnapshot snapshot = inspect(relativePath, source);
        List<CodePreciseOperation> normalized = patch.operations().stream()
                .map(operation -> normalizeOperation(snapshot, operation))
                .toList();
        return new CodePrecisePatch(normalized);
    }

    CodeSymbol resolveSymbol(CodeStructureSnapshot snapshot, String name, String kind, boolean requireInsertion) {
        String normalizedName = normalize(name);
        String normalizedKind = normalize(kind);
        return snapshot.symbols().stream()
                .filter(symbol -> normalizedName.isBlank() || normalize(symbol.name()).equals(normalizedName))
                .filter(symbol -> normalizedKind.isBlank() || normalize(symbol.kind()).equals(normalizedKind))
                .filter(symbol -> !requireInsertion || symbol.supportsInsertion())
                .findFirst()
                .orElseThrow(() -> new PreciseEditException(
                        PreciseEditFailureReason.SYMBOL_NOT_FOUND,
                        "No matching symbol found for precise edit: name=%s kind=%s".formatted(name, kind)
                ));
    }

    private CodePreciseOperation normalizeOperation(CodeStructureSnapshot snapshot, CodePreciseOperation operation) {
        if (operation == null || operation.action() == null || operation.targetSymbol() == null || operation.targetSymbol().isBlank()) {
            return operation;
        }
        List<CodeSymbol> nameMatches = snapshot.symbols().stream()
                .filter(symbol -> normalize(symbol.name()).equals(normalize(operation.targetSymbol())))
                .toList();
        if (nameMatches.isEmpty()) {
            return operation;
        }
        String requestedKind = normalize(operation.targetKind());
        boolean exactMatchExists = requestedKind.isBlank() || nameMatches.stream()
                .anyMatch(symbol -> normalize(symbol.kind()).equals(requestedKind));
        if (exactMatchExists && !requestedKind.isBlank()) {
            return operation;
        }
        CodeSymbol resolved = uniqueSymbolCandidate(nameMatches);
        if (resolved == null) {
            return operation;
        }
        return new CodePreciseOperation(
                operation.action(),
                operation.targetSymbol(),
                resolved.kind(),
                operation.content(),
                operation.contentLines()
        );
    }

    private CodeSymbol uniqueSymbolCandidate(List<CodeSymbol> nameMatches) {
        if (nameMatches.size() == 1) {
            return nameMatches.getFirst();
        }
        List<String> distinctKinds = nameMatches.stream()
                .map(CodeSymbol::kind)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .distinct()
                .toList();
        if (distinctKinds.size() != 1) {
            return null;
        }
        return nameMatches.getFirst();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
