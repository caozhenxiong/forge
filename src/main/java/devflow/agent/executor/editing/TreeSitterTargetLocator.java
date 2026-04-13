package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.CodeStructureSnapshot;
import devflow.agent.parsing.CodeSymbol;
import devflow.agent.parsing.SourceLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 tree-sitter 的通用目标定位器。
 *
 * <p>这层把 JS/TS/Python/Java 等代码文件统一映射成同一种
 * `PatchTargetContext`，避免在 planner / coordinator 里继续按语言散落
 * “列符号、列可插入目标、拼说明文本”的逻辑。
 */
public final class TreeSitterTargetLocator implements TargetLocator {

    private final TreeSitterSupport treeSitterSupport;

    public TreeSitterTargetLocator(TreeSitterSupport treeSitterSupport) {
        this.treeSitterSupport = treeSitterSupport;
    }

    @Override
    public PatchTargetContext locate(Path relativePath, String source) {
        CodeStructureSnapshot snapshot = treeSitterSupport.inspectCodeStructure(relativePath, source == null ? "" : source);
        List<String> targetNames = snapshot.symbols().stream()
                .map(CodeSymbol::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
        List<String> insertableTargetNames = preferredInsertableSymbols(snapshot.symbols()).stream()
                .map(CodeSymbol::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
        return new PatchTargetContext(
                relativePath,
                snapshot.language() == null ? SourceLanguage.UNSUPPORTED : snapshot.language(),
                snapshot.supportsPreciseEditing(),
                targetNames,
                insertableTargetNames,
                renderTargetSummary(snapshot)
        );
    }

    /**
     * 当语言同时暴露容器符号和成员符号时，精确 patch 应优先成员级锚点。
     *
     * <p>例如 JS/Java 的 class + constructor/method 混排时，如果继续把类本身也交给
     * edit-unit planner，后续 strict single-symbol 单元就会退化成“改整个类 body”，
     * 很容易诱发 scope violation 和重复 wrapper。这里优先保留叶子成员符号，把容器符号
     * 只作为 target summary 展示，而不作为默认 insertable target。
     */
    private List<CodeSymbol> preferredInsertableSymbols(List<CodeSymbol> symbols) {
        List<CodeSymbol> insertableSymbols = symbols == null
                ? List.of()
                : symbols.stream()
                        .filter(CodeSymbol::supportsInsertion)
                        .toList();
        if (insertableSymbols.isEmpty()) {
            return List.of();
        }
        List<CodeSymbol> containerSymbols = insertableSymbols.stream()
                .filter(this::isContainerLikeSymbol)
                .toList();
        List<CodeSymbol> constructorSymbols = insertableSymbols.stream()
                .filter(this::isConstructorSymbol)
                .toList();
        List<CodeSymbol> nonConstructorLeafSymbols = insertableSymbols.stream()
                .filter(symbol -> !isContainerLikeSymbol(symbol) && !isConstructorSymbol(symbol))
                .toList();
        if (!nonConstructorLeafSymbols.isEmpty()) {
            return reorderLeafSymbols(nonConstructorLeafSymbols);
        }
        if (!containerSymbols.isEmpty() && !constructorSymbols.isEmpty()) {
            return containerSymbols;
        }
        return insertableSymbols;
    }

    private boolean isMemberLikeSymbol(CodeSymbol symbol) {
        if (symbol == null || symbol.kind() == null) {
            return false;
        }
        return "method".equals(symbol.kind()) || "constructor".equals(symbol.kind());
    }

    private boolean isConstructorSymbol(CodeSymbol symbol) {
        if (symbol == null) {
            return false;
        }
        if ("constructor".equals(symbol.kind())) {
            return true;
        }
        return "method".equals(symbol.kind()) && "constructor".equals(symbol.name());
    }

    private boolean isContainerLikeSymbol(CodeSymbol symbol) {
        if (symbol == null || symbol.kind() == null) {
            return false;
        }
        return "class".equals(symbol.kind()) || "type".equals(symbol.kind());
    }

    private boolean isNonConstructorMember(CodeSymbol symbol) {
        if (symbol == null || symbol.kind() == null) {
            return false;
        }
        return "method".equals(symbol.kind()) && symbol.name() != null && !"constructor".equals(symbol.name());
    }

    /**
     * 当文件同时包含类成员和顶层函数时，优先把成员级锚点排到前面。
     *
     * <p>这样 edit-unit planner 在按批次切分时，会先得到同一结构域内的一组方法，
     * 而不是把 `main + constructor + methods` 这类跨层级目标塞进同一个单元。
     */
    private List<CodeSymbol> reorderLeafSymbols(List<CodeSymbol> symbols) {
        if (symbols == null || symbols.size() <= 1) {
            return symbols == null ? List.of() : symbols;
        }
        List<CodeSymbol> memberSymbols = new ArrayList<>();
        List<CodeSymbol> otherSymbols = new ArrayList<>();
        for (CodeSymbol symbol : symbols) {
            if (isMemberLikeSymbol(symbol)) {
                memberSymbols.add(symbol);
            } else {
                otherSymbols.add(symbol);
            }
        }
        if (memberSymbols.isEmpty() || otherSymbols.isEmpty()) {
            return symbols;
        }
        List<CodeSymbol> reordered = new ArrayList<>(symbols.size());
        reordered.addAll(memberSymbols);
        reordered.addAll(otherSymbols);
        return List.copyOf(reordered);
    }

    private String renderTargetSummary(CodeStructureSnapshot snapshot) {
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
}
