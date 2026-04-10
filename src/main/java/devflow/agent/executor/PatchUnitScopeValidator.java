package devflow.agent.executor;

import devflow.agent.editing.PreciseEditException;
import devflow.agent.editing.PreciseEditFailureReason;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一校验 patch 是否越过当前 edit unit 的责任边界。
 *
 * <p>这层属于 deterministic guard：
 * 1. append-only 单元只能追加；
 * 2. append-only 单元不能突破自己的符号预算；
 * 3. restricted 单元只能修改 allowedSymbols 中的现有符号。
 *
 * <p>这样代码文件、内联脚本、内联样式都复用同一套范围校验，
 * 不再把边界判断散落在各条执行分支里。
 */
final class PatchUnitScopeValidator {

    private final PatchContextBuilder patchContextBuilder;

    PatchUnitScopeValidator(PatchContextBuilder patchContextBuilder) {
        this.patchContextBuilder = patchContextBuilder;
    }

    void validate(Path relativePath, EditUnit unit, List<PatchOperation> operations) {
        if (relativePath == null || unit == null || operations == null || operations.isEmpty()) {
            return;
        }
        if (!unit.restrictsSymbols()) {
            validateAppendOnlyUnit(relativePath, unit, operations);
            return;
        }
        validateRestrictedUnit(relativePath, unit, operations);
    }

    private void validateAppendOnlyUnit(Path relativePath, EditUnit unit, List<PatchOperation> operations) {
        boolean hasIllegalOperation = operations.stream()
                .anyMatch(operation -> operation != null && operation.operationType() != PatchOperationType.APPEND_BLOCK);
        if (hasIllegalOperation) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.EDIT_UNIT_SCOPE_VIOLATION,
                    "Edit unit %s for %s is append-only, but patch contains non-append operations."
                            .formatted(unit.label(), relativePath)
            );
        }
        if (unit.appendSymbolBudget() <= 0) {
            return;
        }
        int appendedSymbols = operations.stream()
                .filter(operation -> operation != null && operation.operationType() == PatchOperationType.APPEND_BLOCK)
                .map(operation -> String.join("\n", operation.contentLines()))
                .mapToInt(content -> countTopLevelSymbols(relativePath, content))
                .sum();
        if (appendedSymbols > unit.appendSymbolBudget()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.EDIT_UNIT_SCOPE_VIOLATION,
                    "Append-only unit %s for %s exceeded append symbol budget %d with %d top-level symbols."
                            .formatted(unit.label(), relativePath, unit.appendSymbolBudget(), appendedSymbols)
            );
        }
    }

    private void validateRestrictedUnit(Path relativePath, EditUnit unit, List<PatchOperation> operations) {
        List<String> allowedSymbols = unit.allowedSymbols();
        boolean hasOutOfScopeOperation = operations.stream()
                .anyMatch(operation -> {
                    if (operation.operationType() == PatchOperationType.APPEND_BLOCK) {
                        return true;
                    }
                    return operation.target() == null
                            || operation.target().symbolName() == null
                            || operation.target().symbolName().isBlank()
                            || !allowedSymbols.contains(operation.target().symbolName());
                });
        if (hasOutOfScopeOperation) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.EDIT_UNIT_SCOPE_VIOLATION,
                    "Patch targets symbols outside current edit unit %s for %s. allowed=%s"
                            .formatted(unit.label(), relativePath, String.join(", ", allowedSymbols))
            );
        }
    }

    /**
     * append-only 单元也要用结构分析统计新增的顶层符号数量，
     * 不能依赖模型“自觉只补一个 helper”。
     */
    private int countTopLevelSymbols(Path relativePath, String content) {
        try {
            return patchContextBuilder.topLevelCodeSymbolCount(relativePath, content);
        } catch (Exception ignored) {
            return 0;
        }
    }
}
