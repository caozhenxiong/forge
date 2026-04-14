package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * patch 预算相关的集中设置。
 *
 * <p>这层先把最容易散落的 patch 阈值收成一个稳定对象：
 * 1. 不可再拆分单元的收紧预算；
 * 2. 每个允许目标对应的基础预算；
 * 3. 发送前预裁剪的最大预算。
 *
 * <p>当前先支持通过系统属性覆写，避免继续在协调器和策略类里写死魔法数字。
 * 后续如果接 Spring properties，可以直接把这一层作为最终配置载体。
 */
@ConfigurationProperties(prefix = "devflow.patch.budget")
public record PatchBudgetSettings(
        double unitBudgetRatioPerTarget,
        double maxPreFlightUnitRatio
) {

    private static final double DEFAULT_UNIT_BUDGET_RATIO_PER_TARGET = 0.25d;
    private static final double DEFAULT_MAX_PREFLIGHT_UNIT_RATIO = 0.75d;

    public PatchBudgetSettings() {
        this(DEFAULT_UNIT_BUDGET_RATIO_PER_TARGET, DEFAULT_MAX_PREFLIGHT_UNIT_RATIO);
    }

    public static PatchBudgetSettings defaults() {
        return new PatchBudgetSettings();
    }

    public PatchBudgetSettings {
        unitBudgetRatioPerTarget = normalizeRatio(unitBudgetRatioPerTarget, DEFAULT_UNIT_BUDGET_RATIO_PER_TARGET);
        maxPreFlightUnitRatio = normalizeRatio(maxPreFlightUnitRatio, DEFAULT_MAX_PREFLIGHT_UNIT_RATIO);
    }

    private static double normalizeRatio(double value, double fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(1.0d, value);
    }
}
