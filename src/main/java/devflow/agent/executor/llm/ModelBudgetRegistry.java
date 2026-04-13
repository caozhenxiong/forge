package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationBudgetProperties;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 按模型名解析预算配置。
 *
 * <p>这里不试图猜“这个模型能输出多少行代码”，而是先把模型级 token 预算抽成统一配置。
 * 调用侧只拿到一个稳定的 {@link ModelBudgetProfile}，再结合 prompt 大小做输出裁剪。
 */
@Component
public class ModelBudgetRegistry {

    private final GenerationBudgetProperties properties;

    public ModelBudgetRegistry(GenerationBudgetProperties properties) {
        this.properties = properties;
    }

    public ModelBudgetProfile resolve(String modelName) {
        GenerationBudgetProperties.Defaults defaults = properties.defaults();
        GenerationBudgetProperties.Override override = findBestOverride(modelName, properties.models());
        return new ModelBudgetProfile(
                chooseInt(override == null ? null : override.contextWindowTokens(), defaults.contextWindowTokens()),
                chooseDouble(override == null ? null : override.safeOutputRatio(), defaults.safeOutputRatio()),
                chooseDouble(override == null ? null : override.reserveRatio(), defaults.reserveRatio()),
                chooseInt(override == null ? null : override.minimumReserveTokens(), defaults.minimumReserveTokens()),
                chooseInt(override == null ? null : override.maximumReserveTokens(), defaults.maximumReserveTokens()),
                chooseInt(override == null ? null : override.minimumOutputTokens(), defaults.minimumOutputTokens()),
                chooseDouble(override == null ? null : override.charsPerToken(), defaults.charsPerToken())
        );
    }

    private GenerationBudgetProperties.Override findBestOverride(
            String modelName,
            Map<String, GenerationBudgetProperties.Override> overrides
    ) {
        if (modelName == null || modelName.isBlank() || overrides.isEmpty()) {
            return null;
        }
        GenerationBudgetProperties.Override match = null;
        int longest = -1;
        for (Map.Entry<String, GenerationBudgetProperties.Override> entry : overrides.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (modelName.startsWith(key) && key.length() > longest) {
                longest = key.length();
                match = entry.getValue();
            }
        }
        return match;
    }

    private int chooseInt(Integer override, int fallback) {
        return override == null || override <= 0 ? fallback : override;
    }

    private double chooseDouble(Double override, double fallback) {
        return override == null || override <= 0 ? fallback : override;
    }
}
