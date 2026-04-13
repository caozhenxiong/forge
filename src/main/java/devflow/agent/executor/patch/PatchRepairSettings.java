package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationBudgetProfile;

/**
 * repair-before-regenerate 的运行时配置。
 *
 * <p>这里把 repair 尝试次数和预算比例收口，避免再次把小常量散到执行器里。
 */
public final class PatchRepairSettings {

    private static final String PREFIX = "devflow.patch-repair.";
    private static final int DEFAULT_JSON_REPAIR_ATTEMPTS = 1;
    private static final int DEFAULT_SEMANTIC_REPAIR_ATTEMPTS = 1;
    private static final int DEFAULT_SYNTAX_REPAIR_ATTEMPTS = 1;
    private static final double DEFAULT_JSON_REPAIR_OUTPUT_RATIO = GenerationBudgetProfile.patchOutputRatio();
    private static final double DEFAULT_SEMANTIC_REPAIR_OUTPUT_RATIO = GenerationBudgetProfile.patchOutputRatio();
    private static final double DEFAULT_SYNTAX_REPAIR_OUTPUT_RATIO = GenerationBudgetProfile.fullBudgetRatio();
    private static final double DEFAULT_REPAIR_CHARS_PER_TOKEN = 3.2d;
    private static final double DEFAULT_JSON_REPAIR_GROWTH_FACTOR = 1.15d;
    private static final double DEFAULT_SYNTAX_REPAIR_GROWTH_FACTOR = 1.10d;
    private static final int DEFAULT_JSON_REPAIR_PADDING_TOKENS = 64;
    private static final int DEFAULT_SYNTAX_REPAIR_PADDING_TOKENS = 96;
    private static final int DEFAULT_MINIMUM_REPAIR_NUM_PREDICT = 160;

    public int jsonModelRepairAttempts() {
        return readPositiveInt(PREFIX + "json-model-repair-attempts", DEFAULT_JSON_REPAIR_ATTEMPTS);
    }

    public int syntaxModelRepairAttempts() {
        return readPositiveInt(PREFIX + "syntax-model-repair-attempts", DEFAULT_SYNTAX_REPAIR_ATTEMPTS);
    }

    public int semanticModelRepairAttempts() {
        return readPositiveInt(PREFIX + "semantic-model-repair-attempts", DEFAULT_SEMANTIC_REPAIR_ATTEMPTS);
    }

    public double jsonRepairOutputRatio() {
        return readPositiveDouble(PREFIX + "json-repair-output-ratio", DEFAULT_JSON_REPAIR_OUTPUT_RATIO);
    }

    public double syntaxRepairOutputRatio() {
        return readPositiveDouble(PREFIX + "syntax-repair-output-ratio", DEFAULT_SYNTAX_REPAIR_OUTPUT_RATIO);
    }

    public double semanticRepairOutputRatio() {
        return readPositiveDouble(PREFIX + "semantic-repair-output-ratio", DEFAULT_SEMANTIC_REPAIR_OUTPUT_RATIO);
    }

    public int estimateJsonRepairNumPredict(String payload) {
        return estimateRepairNumPredict(
                payload,
                DEFAULT_JSON_REPAIR_GROWTH_FACTOR,
                DEFAULT_JSON_REPAIR_PADDING_TOKENS,
                "json-repair-growth-factor",
                "json-repair-padding-tokens"
        );
    }

    public int estimateSyntaxRepairNumPredict(String content) {
        return estimateRepairNumPredict(
                content,
                DEFAULT_SYNTAX_REPAIR_GROWTH_FACTOR,
                DEFAULT_SYNTAX_REPAIR_PADDING_TOKENS,
                "syntax-repair-growth-factor",
                "syntax-repair-padding-tokens"
        );
    }

    private int estimateRepairNumPredict(
            String content,
            double defaultGrowthFactor,
            int defaultPaddingTokens,
            String growthFactorKeySuffix,
            String paddingTokensKeySuffix
    ) {
        int estimatedTokens = estimateTokens(content);
        double growthFactor = readPositiveDouble(PREFIX + growthFactorKeySuffix, defaultGrowthFactor);
        int paddingTokens = readPositiveInt(PREFIX + paddingTokensKeySuffix, defaultPaddingTokens);
        int minimum = readPositiveInt(PREFIX + "minimum-repair-num-predict", DEFAULT_MINIMUM_REPAIR_NUM_PREDICT);
        int scaled = (int) Math.ceil(estimatedTokens * growthFactor);
        return Math.max(minimum, scaled + Math.max(0, paddingTokens));
    }

    private int estimateTokens(String content) {
        int totalChars = content == null ? 0 : content.length();
        if (totalChars <= 0) {
            return DEFAULT_MINIMUM_REPAIR_NUM_PREDICT;
        }
        double charsPerToken = readPositiveDouble(PREFIX + "repair-chars-per-token", DEFAULT_REPAIR_CHARS_PER_TOKEN);
        return Math.max(1, (int) Math.ceil(totalChars / charsPerToken));
    }

    private static int readPositiveInt(String key, int fallback) {
        Integer configured = Integer.getInteger(key);
        return configured == null || configured <= 0 ? fallback : configured;
    }

    private static double readPositiveDouble(String key, double fallback) {
        String configured = System.getProperty(key);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(configured.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
