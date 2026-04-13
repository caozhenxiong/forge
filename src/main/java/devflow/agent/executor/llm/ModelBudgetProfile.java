package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 模型预算配置的运行时快照。
 *
 * <p>把可配置项收敛成不可变对象后，后续预算计算器和校准器都只需要消费这一层，
 * 避免把“默认值/覆写值/合法化逻辑”散到多个执行类里。
 */
public record ModelBudgetProfile(
        int contextWindowTokens,
        double safeOutputRatio,
        double reserveRatio,
        int minimumReserveTokens,
        int maximumReserveTokens,
        int minimumOutputTokens,
        double charsPerToken
) {

    public int reserveTokens() {
        int scaled = (int) Math.round(contextWindowTokens * reserveRatio);
        return Math.max(minimumReserveTokens, Math.min(maximumReserveTokens, scaled));
    }

    public int safeOutputCeilingTokens(int availableTokens) {
        int scaled = (int) Math.round(availableTokens * safeOutputRatio);
        return Math.max(minimumOutputTokens, Math.min(availableTokens, scaled));
    }
}
