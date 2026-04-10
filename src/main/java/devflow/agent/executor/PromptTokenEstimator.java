package devflow.agent.executor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 对 prompt token 做保守估算，并利用运行时回执持续校准。
 *
 * <p>这里的目标不是精确预测“能返回多少行代码”，而是把 prompt 大小转换成稳定的 token 预算，
 * 让系统在发请求前就知道还剩多少输出窗口。
 */
@Component
public class PromptTokenEstimator {

    private final PromptTokenEstimatorSettings settings;
    private final Map<String, Double> calibratedCharsPerToken = new ConcurrentHashMap<>();

    public PromptTokenEstimator() {
        this(PromptTokenEstimatorSettings.defaults());
    }

    PromptTokenEstimator(PromptTokenEstimatorSettings settings) {
        this.settings = settings;
    }

    public int estimateTokens(String modelName, ModelBudgetProfile budgetProfile, String content) {
        int totalChars = safeLength(content);
        if (totalChars <= 0) {
            return 0;
        }
        double charsPerToken = calibratedCharsPerToken.getOrDefault(modelName, budgetProfile.charsPerToken());
        return Math.max(1, (int) Math.ceil(totalChars / Math.max(settings.minCharsPerToken(), charsPerToken)));
    }

    public int estimatePromptTokens(String modelName, ModelBudgetProfile budgetProfile, String systemPrompt, String userPrompt) {
        return estimateTokens(modelName, budgetProfile, systemPrompt) + estimateTokens(modelName, budgetProfile, userPrompt);
    }

    public void observePromptUsage(String modelName, String systemPrompt, String userPrompt, Integer promptEvalCount) {
        if (modelName == null || modelName.isBlank() || promptEvalCount == null || promptEvalCount <= 0) {
            return;
        }
        int totalChars = safeLength(systemPrompt) + safeLength(userPrompt);
        if (totalChars <= 0) {
            return;
        }
        double observed = clamp((double) totalChars / promptEvalCount);
        calibratedCharsPerToken.merge(
                modelName,
                observed,
                (current, next) -> clamp((current * (1.0d - settings.learningRate()))
                        + (next * settings.learningRate()))
        );
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private double clamp(double value) {
        return Math.max(settings.minCharsPerToken(), Math.min(settings.maxCharsPerToken(), value));
    }
}
