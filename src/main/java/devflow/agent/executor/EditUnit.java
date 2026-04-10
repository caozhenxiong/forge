package devflow.agent.executor;

import java.util.List;

/**
 * 单次精确编辑允许触达的最小工作单元。
 *
 * <p>系统通过 edit unit 硬限制模型当前可改动的符号范围，
 * 避免继续把整个代码文件或整段内联脚本作为一次生成单元交给模型。
 */
record EditUnit(
        EditUnitKind kind,
        String label,
        List<String> allowedSymbols,
        int appendSymbolBudget,
        int splitDepth
) {
    EditUnit(
            EditUnitKind kind,
            String label,
            List<String> allowedSymbols
    ) {
        this(kind, label, allowedSymbols, 0, 0);
    }

    EditUnit {
        allowedSymbols = allowedSymbols == null ? List.of() : List.copyOf(allowedSymbols);
        appendSymbolBudget = Math.max(0, appendSymbolBudget);
        splitDepth = Math.max(0, splitDepth);
    }

    EditUnit(
            EditUnitKind kind,
            String label,
            List<String> allowedSymbols,
            int appendSymbolBudget
    ) {
        this(kind, label, allowedSymbols, appendSymbolBudget, 0);
    }

    boolean restrictsSymbols() {
        return !allowedSymbols.isEmpty();
    }

    boolean appendOnly() {
        return allowedSymbols.isEmpty();
    }

    boolean splittable() {
        return allowedSymbols.size() > 1 || appendSymbolBudget > 1;
    }

    boolean reducedUnit() {
        return splitDepth > 0;
    }

    EditUnit splitChild(String splitLabel, List<String> splitSymbols, int splitAppendBudget) {
        return new EditUnit(kind, splitLabel, splitSymbols, splitAppendBudget, splitDepth + 1);
    }
}
