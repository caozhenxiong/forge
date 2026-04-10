package devflow.agent.executor;

/**
 * 统一维护 prompt token 估算器的校准参数。
 *
 * <p>这些阈值本来散在 {@link PromptTokenEstimator} 内部，后续一旦要按模型、
 * provider 或运行环境微调，就会重新长成魔法数字。这里先集中收口，并允许
 * 通过 system properties 做轻量覆盖。
 */
record PromptTokenEstimatorSettings(
        double minCharsPerToken,
        double maxCharsPerToken,
        double learningRate
) {

    private static final String MIN_CHARS_PER_TOKEN_KEY = "devflow.prompt-estimator.min-chars-per-token";
    private static final String MAX_CHARS_PER_TOKEN_KEY = "devflow.prompt-estimator.max-chars-per-token";
    private static final String LEARNING_RATE_KEY = "devflow.prompt-estimator.learning-rate";

    static PromptTokenEstimatorSettings defaults() {
        return new PromptTokenEstimatorSettings(
                readPositiveDouble(MIN_CHARS_PER_TOKEN_KEY, 1.8d),
                readPositiveDouble(MAX_CHARS_PER_TOKEN_KEY, 6.0d),
                readBoundedRate(LEARNING_RATE_KEY, 0.25d)
        );
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

    private static double readBoundedRate(String key, double fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return value > 0 && value <= 1.0d ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
