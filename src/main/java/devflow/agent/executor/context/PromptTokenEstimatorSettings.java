package devflow.agent.executor.context;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一维护 prompt token 估算器的校准参数。
 *
 * <p>这些阈值本来散在 {@link PromptTokenEstimator} 内部，后续一旦要按模型、
 * provider 或运行环境微调，就会重新长成魔法数字。这里先集中收口，并允许
 * 通过 system properties 做轻量覆盖。
 */
@ConfigurationProperties(prefix = "devflow.prompt-estimator")
public record PromptTokenEstimatorSettings(
        double minCharsPerToken,
        double maxCharsPerToken,
        double learningRate
) {

    private static final double DEFAULT_MIN_CHARS_PER_TOKEN = 1.8d;
    private static final double DEFAULT_MAX_CHARS_PER_TOKEN = 6.0d;
    private static final double DEFAULT_LEARNING_RATE = 0.25d;

    public PromptTokenEstimatorSettings() {
        this(DEFAULT_MIN_CHARS_PER_TOKEN, DEFAULT_MAX_CHARS_PER_TOKEN, DEFAULT_LEARNING_RATE);
    }

    public PromptTokenEstimatorSettings {
        minCharsPerToken = normalizePositive(minCharsPerToken, DEFAULT_MIN_CHARS_PER_TOKEN);
        maxCharsPerToken = Math.max(minCharsPerToken, normalizePositive(maxCharsPerToken, DEFAULT_MAX_CHARS_PER_TOKEN));
        learningRate = normalizeBoundedRate(learningRate, DEFAULT_LEARNING_RATE);
    }

    private static double normalizePositive(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    private static double normalizeBoundedRate(double value, double fallback) {
        return value > 0 && value <= 1.0d ? value : fallback;
    }
}
