package devflow.agent.editing.precise;

import devflow.agent.parsing.CodeStructureSnapshot;
import devflow.agent.parsing.CodeSymbol;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 精确编辑里的符号探测与目标归一化支撑。
 *
 * <p>这里统一负责：
 * 1. 读取 tree-sitter 结构快照；
 * 2. 暴露稳定的符号摘要；
 * 3. 提供局部编辑规划所需的稳定符号集合。
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
}
