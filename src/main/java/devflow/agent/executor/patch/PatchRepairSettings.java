package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * repair-before-regenerate 的运行时配置。
 *
 * <p>这里把 repair 尝试次数和预算比例收口，避免再次把小常量散到执行器里。
 */
@ConfigurationProperties(prefix = "devflow.patch.repair")
public record PatchRepairSettings(
        int jsonModelRepairAttempts,
        int semanticModelRepairAttempts,
        int syntaxModelRepairAttempts,
        double jsonRepairOutputRatio,
        double semanticRepairOutputRatio,
        double syntaxRepairOutputRatio,
        double repairCharsPerToken,
        double jsonRepairGrowthFactor,
        double syntaxRepairGrowthFactor,
        int jsonRepairPaddingTokens,
        int syntaxRepairPaddingTokens,
        int minimumRepairNumPredict
) {

    private static final int DEFAULT_JSON_REPAIR_ATTEMPTS = 1;
    private static final int DEFAULT_SEMANTIC_REPAIR_ATTEMPTS = 1;
    private static final int DEFAULT_SYNTAX_REPAIR_ATTEMPTS = 1;
    private static final double DEFAULT_JSON_REPAIR_OUTPUT_RATIO = 0.6d;
    private static final double DEFAULT_SEMANTIC_REPAIR_OUTPUT_RATIO = 0.6d;
    private static final double DEFAULT_SYNTAX_REPAIR_OUTPUT_RATIO = 1.0d;
    private static final double DEFAULT_REPAIR_CHARS_PER_TOKEN = 3.2d;
    private static final double DEFAULT_JSON_REPAIR_GROWTH_FACTOR = 1.15d;
    private static final double DEFAULT_SYNTAX_REPAIR_GROWTH_FACTOR = 1.10d;
    private static final int DEFAULT_JSON_REPAIR_PADDING_TOKENS = 64;
    private static final int DEFAULT_SYNTAX_REPAIR_PADDING_TOKENS = 96;
    private static final int DEFAULT_MINIMUM_REPAIR_NUM_PREDICT = 160;

    public PatchRepairSettings() {
        this(
                DEFAULT_JSON_REPAIR_ATTEMPTS,
                DEFAULT_SEMANTIC_REPAIR_ATTEMPTS,
                DEFAULT_SYNTAX_REPAIR_ATTEMPTS,
                DEFAULT_JSON_REPAIR_OUTPUT_RATIO,
                DEFAULT_SEMANTIC_REPAIR_OUTPUT_RATIO,
                DEFAULT_SYNTAX_REPAIR_OUTPUT_RATIO,
                DEFAULT_REPAIR_CHARS_PER_TOKEN,
                DEFAULT_JSON_REPAIR_GROWTH_FACTOR,
                DEFAULT_SYNTAX_REPAIR_GROWTH_FACTOR,
                DEFAULT_JSON_REPAIR_PADDING_TOKENS,
                DEFAULT_SYNTAX_REPAIR_PADDING_TOKENS,
                DEFAULT_MINIMUM_REPAIR_NUM_PREDICT
        );
    }

    public PatchRepairSettings {
        jsonModelRepairAttempts = normalizePositive(jsonModelRepairAttempts, DEFAULT_JSON_REPAIR_ATTEMPTS);
        semanticModelRepairAttempts = normalizePositive(semanticModelRepairAttempts, DEFAULT_SEMANTIC_REPAIR_ATTEMPTS);
        syntaxModelRepairAttempts = normalizePositive(syntaxModelRepairAttempts, DEFAULT_SYNTAX_REPAIR_ATTEMPTS);
        jsonRepairOutputRatio = normalizePositiveRatio(jsonRepairOutputRatio, DEFAULT_JSON_REPAIR_OUTPUT_RATIO);
        semanticRepairOutputRatio = normalizePositiveRatio(semanticRepairOutputRatio, DEFAULT_SEMANTIC_REPAIR_OUTPUT_RATIO);
        syntaxRepairOutputRatio = normalizePositiveRatio(syntaxRepairOutputRatio, DEFAULT_SYNTAX_REPAIR_OUTPUT_RATIO);
        repairCharsPerToken = normalizePositive(repairCharsPerToken, DEFAULT_REPAIR_CHARS_PER_TOKEN);
        jsonRepairGrowthFactor = normalizePositive(jsonRepairGrowthFactor, DEFAULT_JSON_REPAIR_GROWTH_FACTOR);
        syntaxRepairGrowthFactor = normalizePositive(syntaxRepairGrowthFactor, DEFAULT_SYNTAX_REPAIR_GROWTH_FACTOR);
        jsonRepairPaddingTokens = normalizeNonNegative(jsonRepairPaddingTokens, DEFAULT_JSON_REPAIR_PADDING_TOKENS);
        syntaxRepairPaddingTokens = normalizeNonNegative(syntaxRepairPaddingTokens, DEFAULT_SYNTAX_REPAIR_PADDING_TOKENS);
        minimumRepairNumPredict = normalizePositive(minimumRepairNumPredict, DEFAULT_MINIMUM_REPAIR_NUM_PREDICT);
    }

    public int estimateJsonRepairNumPredict(String payload) {
        return estimateRepairNumPredict(
                payload,
                jsonRepairGrowthFactor,
                jsonRepairPaddingTokens
        );
    }

    public int estimateSyntaxRepairNumPredict(String content) {
        return estimateRepairNumPredict(
                content,
                syntaxRepairGrowthFactor,
                syntaxRepairPaddingTokens
        );
    }

    private int estimateRepairNumPredict(
            String content,
            double growthFactor,
            int paddingTokens
    ) {
        int estimatedTokens = estimateTokens(content);
        int scaled = (int) Math.ceil(estimatedTokens * normalizePositive(growthFactor, DEFAULT_JSON_REPAIR_GROWTH_FACTOR));
        return Math.max(minimumRepairNumPredict, scaled + Math.max(0, paddingTokens));
    }

    private int estimateTokens(String content) {
        int totalChars = content == null ? 0 : content.length();
        if (totalChars <= 0) {
            return minimumRepairNumPredict;
        }
        return Math.max(1, (int) Math.ceil(totalChars / repairCharsPerToken));
    }

    private static int normalizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static int normalizeNonNegative(int value, int fallback) {
        return value >= 0 ? value : fallback;
    }

    private static double normalizePositive(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    private static double normalizePositiveRatio(double value, double fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(1.0d, value);
    }
}
