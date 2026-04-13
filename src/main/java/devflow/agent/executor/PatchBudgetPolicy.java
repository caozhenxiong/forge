package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.context.OutputBudgetCalculator;
import devflow.agent.executor.llm.ModelBudgetRegistry;

/**
 * 统一维护 patch 单元级输出预算。
 *
 * <p>provider 侧的全局 token 裁剪仍由
 * {@link ModelBudgetRegistry} / {@link OutputBudgetCalculator} 负责，
 * 这里负责的是“当前 patch 单元最大应该放多宽”，避免调用方继续散落
 * `append-only 要不要更窄`、`单符号是否要降预算` 这类判断。
 */
final class PatchBudgetPolicy {

    private final PatchBudgetSettings settings;

    PatchBudgetPolicy() {
        this(PatchBudgetSettings.defaults());
    }

    PatchBudgetPolicy(PatchBudgetSettings settings) {
        this.settings = settings;
    }

    double outputBudgetRatioForUnit(EditUnit unit, double defaultOutputBudgetRatio) {
        if (unit == null || !unit.restrictsSymbols()) {
            if (unit != null && unit.appendOnly() && unit.appendSymbolBudget() > 0) {
                if (!unit.splittable()) {
                    return defaultOutputBudgetRatio;
                }
                return Math.min(
                        defaultOutputBudgetRatio,
                        Math.min(1.0d, unit.appendSymbolBudget() * settings.unitBudgetRatioPerTarget())
                );
            }
            return defaultOutputBudgetRatio;
        }
        if (!unit.splittable()) {
            return defaultOutputBudgetRatio;
        }
        return Math.min(
                defaultOutputBudgetRatio,
                Math.min(1.0d, unit.allowedSymbols().size() * settings.unitBudgetRatioPerTarget())
        );
    }

}
