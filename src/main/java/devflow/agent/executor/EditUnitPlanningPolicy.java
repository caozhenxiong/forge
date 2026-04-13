package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 收敛 EditUnit 切分策略的稳定默认值。
 *
 * <p>这类阈值直接决定防截断内核的执行粒度，应集中到同一处维护，
 * 避免 file coordinator、delivery policy 和 edit-unit planner 各写各的。
 */
public final class EditUnitPlanningPolicy {

    private static final int DEFAULT_MAX_SYMBOLS_PER_UNIT = 4;
    private static final int DEFAULT_INLINE_APPEND_SYMBOL_BUDGET = 2;
    /**
     * 发送前预裁剪不应该再把 patch 单元压到几百 token。
     *
     * <p>这层只负责提前拆掉“明显过宽”的单元，不应该抢在 provider 预算之前
     * 把大部分可用输出空间全部砍掉。
     */
    private static final double DEFAULT_MAX_PRE_FLIGHT_UNIT_RATIO = 0.75d;
    private static final String MAX_SYMBOLS_PER_UNIT_KEY = "devflow.edit-unit.max-symbols-per-unit";
    private static final String INLINE_APPEND_SYMBOL_BUDGET_KEY = "devflow.edit-unit.inline-append-symbol-budget";
    private static final String MAX_PRE_FLIGHT_UNIT_RATIO_KEY = "devflow.edit-unit.max-preflight-unit-ratio";

    private EditUnitPlanningPolicy() {
    }

    public static int maxSymbolsPerUnit() {
        return readPositiveInt(MAX_SYMBOLS_PER_UNIT_KEY, DEFAULT_MAX_SYMBOLS_PER_UNIT);
    }

    public static int inlineAppendSymbolBudget() {
        return readPositiveInt(INLINE_APPEND_SYMBOL_BUDGET_KEY, DEFAULT_INLINE_APPEND_SYMBOL_BUDGET);
    }

    public static double maxPreFlightUnitRatio() {
        return readPositiveDouble(MAX_PRE_FLIGHT_UNIT_RATIO_KEY, DEFAULT_MAX_PRE_FLIGHT_UNIT_RATIO);
    }

    private static int readPositiveInt(String key, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double readPositiveDouble(String key, double fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
