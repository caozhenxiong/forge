package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 收敛 EditUnit 切分策略的稳定默认值。
 *
 * <p>这类阈值直接决定防截断内核的执行粒度，应集中到同一处维护，
 * 避免 file coordinator、delivery policy 和 edit-unit planner 各写各的。
 */
@ConfigurationProperties(prefix = "devflow.edit-unit")
public record EditUnitPlanningPolicy(
        int maxSymbolsPerUnit,
        int inlineAppendSymbolBudget,
        double maxPreFlightUnitRatio
) {

    private static final int DEFAULT_MAX_SYMBOLS_PER_UNIT = 4;
    private static final int DEFAULT_INLINE_APPEND_SYMBOL_BUDGET = 2;
    /**
     * 发送前预裁剪不应该再把 patch 单元压到几百 token。
     *
     * <p>这层只负责提前拆掉“明显过宽”的单元，不应该抢在 provider 预算之前
     * 把大部分可用输出空间全部砍掉。
     */
    private static final double DEFAULT_MAX_PRE_FLIGHT_UNIT_RATIO = 0.75d;
    public EditUnitPlanningPolicy() {
        this(
                DEFAULT_MAX_SYMBOLS_PER_UNIT,
                DEFAULT_INLINE_APPEND_SYMBOL_BUDGET,
                DEFAULT_MAX_PRE_FLIGHT_UNIT_RATIO
        );
    }

    public EditUnitPlanningPolicy {
        maxSymbolsPerUnit = normalizePositive(maxSymbolsPerUnit, DEFAULT_MAX_SYMBOLS_PER_UNIT);
        inlineAppendSymbolBudget = normalizePositive(
                inlineAppendSymbolBudget,
                DEFAULT_INLINE_APPEND_SYMBOL_BUDGET
        );
        maxPreFlightUnitRatio = normalizePositiveRatio(maxPreFlightUnitRatio, DEFAULT_MAX_PRE_FLIGHT_UNIT_RATIO);
    }

    private static int normalizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double normalizePositiveRatio(double value, double fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(1.0d, value);
    }
}
