package devflow.agent.executor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 把文件级工作集继续拆成更小的 edit unit。
 *
 * <p>这层不猜业务语义，也不决定“哪些能力最重要”；
 * 它只根据当前可编辑符号清单，把一次模型生成限制在更小的符号批次内，
 * 以降低截断概率并提高恢复确定性。
 */
class EditUnitPlanner {

    private final TargetLocator targetLocator;
    private final int maxSymbolsPerUnit;

    EditUnitPlanner(TargetLocator targetLocator) {
        this(targetLocator, EditUnitPlanningPolicy.maxSymbolsPerUnit());
    }

    EditUnitPlanner(TargetLocator targetLocator, int maxSymbolsPerUnit) {
        this.targetLocator = targetLocator;
        this.maxSymbolsPerUnit = Math.max(1, maxSymbolsPerUnit);
    }

    TargetLocator targetLocator() {
        return targetLocator;
    }

    List<EditUnit> planCodeUnits(Path relativePath, String source) {
        return batchCodeSymbols(relativePath, source, EditUnitKind.CODE_SYMBOL_BATCH, relativePath + "#code-unit");
    }

    PatchPlan planCodePatch(Path relativePath, String source) {
        return new PatchPlan(relativePath, FileEditStrategyNames.PRECISE_CODE, planCodeUnits(relativePath, source));
    }

    List<EditUnit> planInlineScriptUnits(Path syntheticPath, String source) {
        String labelPrefix = syntheticPath + "#inline-unit";
        // inline-script workset 不再承担 bootstrap 任务；
        // 这里只负责已经具备稳定符号批次的脚本，避免 planner 再次偷偷生成 append/orchestrator 旧骨架。
        return batchInsertableSymbols(
                syntheticPath,
                source,
                EditUnitKind.INLINE_SCRIPT_SYMBOL_BATCH,
                labelPrefix
        );
    }

    PatchPlan planInlineScriptPatch(Path syntheticPath, String source) {
        return new PatchPlan(syntheticPath, FileEditStrategyNames.INLINE_SCRIPT_WORKSET, planInlineScriptUnits(syntheticPath, source));
    }

    private List<EditUnit> batchSymbols(
            Path relativePath,
            String source,
            EditUnitKind kind,
            String labelPrefix
    ) {
        List<String> symbols = targetLocator.locate(relativePath, source).targetNames();
        if (symbols.isEmpty()) {
            return List.of(new EditUnit(kind, labelPrefix + "-all", List.of()));
        }
        if (symbols.size() <= maxSymbolsPerUnit) {
            return List.of(new EditUnit(kind, labelPrefix + "-all", symbols));
        }
        List<EditUnit> units = new ArrayList<>();
        for (int start = 0; start < symbols.size(); start += maxSymbolsPerUnit) {
            int end = Math.min(symbols.size(), start + maxSymbolsPerUnit);
            units.add(new EditUnit(kind, labelPrefix + "-" + (units.size() + 1), symbols.subList(start, end)));
        }
        return units;
    }

    /**
     * 代码文件的精确编辑主链应优先锚定“可稳定插入/替换 body”的符号。
     *
     * <p>如果直接把所有 targetNames 下发给模型，函数内局部变量也会被规划成 edit unit，
     * 但 precise patch apply 并不稳定支持这类锚点，最终会在实现阶段反复触发
     * SYMBOL_NOT_FOUND / EDIT_UNIT_SCOPE_VIOLATION。只有在源码里完全不存在 insertable symbol
     * 时，才回退到更宽的 targetNames 集合。
     */
    private List<EditUnit> batchCodeSymbols(
            Path relativePath,
            String source,
            EditUnitKind kind,
            String labelPrefix
    ) {
        PatchTargetContext targetContext = targetLocator.locate(relativePath, source);
        List<String> symbols = targetContext.insertableTargetNames().isEmpty()
                ? targetContext.targetNames()
                : targetContext.insertableTargetNames();
        if (symbols.isEmpty()) {
            return List.of(new EditUnit(kind, labelPrefix + "-all", List.of()));
        }
        List<EditUnit> units = new ArrayList<>();
        for (String symbol : symbols) {
            units.add(new EditUnit(kind, labelPrefix + "-" + (units.size() + 1), List.of(symbol)));
        }
        return units;
    }

    /**
     * 内联脚本比普通代码文件更容易退化成“大段 script 重写”。
     *
     * <p>因此这里只接受支持插入/替换 body 的稳定符号，避免把局部变量声明等脆弱锚点
     * 继续下发给模型，导致 working-set 路径反复命中 SYMBOL_NOT_FOUND。
     */
    private List<EditUnit> batchInsertableSymbols(
            Path relativePath,
            String source,
            EditUnitKind kind,
            String labelPrefix
    ) {
        List<String> symbols = targetLocator.locate(relativePath, source).insertableTargetNames();
        if (symbols.isEmpty()) {
            return List.of();
        }
        if (symbols.size() <= maxSymbolsPerUnit) {
            return List.of(new EditUnit(kind, labelPrefix + "-all", symbols));
        }
        List<EditUnit> units = new ArrayList<>();
        for (int start = 0; start < symbols.size(); start += maxSymbolsPerUnit) {
            int end = Math.min(symbols.size(), start + maxSymbolsPerUnit);
            units.add(new EditUnit(kind, labelPrefix + "-" + (units.size() + 1), symbols.subList(start, end)));
        }
        return units;
    }
}
