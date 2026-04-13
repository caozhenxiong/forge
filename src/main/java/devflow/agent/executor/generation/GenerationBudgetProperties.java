package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一维护“模型输出预算”相关的可配置项。
 *
 * <p>这里配置的是模型级预算，而不是某个具体 prompt 的业务预算。
 * 业务侧仍然可以给出期望的 `num_predict`，但真正发请求前必须再经过模型预算裁剪，
 * 避免不同模型在相同输出请求下反复因为上下文窗口不足而截断。
 */
@ConfigurationProperties(prefix = "devflow.ollama.budget")
public record GenerationBudgetProperties(
        Defaults defaults,
        Map<String, Override> models
) {

    public GenerationBudgetProperties {
        defaults = defaults == null ? Defaults.defaultValues() : defaults.normalized();
        Map<String, Override> merged = new LinkedHashMap<>(builtinOverrides());
        if (models != null) {
            merged.putAll(models);
        }
        models = Map.copyOf(merged);
    }

    private static Map<String, Override> builtinOverrides() {
        Map<String, Override> overrides = new LinkedHashMap<>();
        // 对 24GB 显存机器先做保守上调，避免直接冲到 64k context 后把部分 KV cache 挤到 CPU。
        // 安全输出上限不再使用固定 token，而是按“可用输出 * ratio”动态计算。
        overrides.put("qwen3-coder", new Override(36_864, 0.75d, 0.05d, 1_200, 4_096, 160, 3.2d));
        overrides.put("qwen3.5", new Override(32_768, 0.20d, 0.05d, 1_024, 4_096, 160, 3.4d));
        overrides.put("gemma4", new Override(32_768, 0.18d, 0.05d, 1_024, 4_096, 160, 3.0d));
        return overrides;
    }

    public record Defaults(
            int contextWindowTokens,
            double safeOutputRatio,
            double reserveRatio,
            int minimumReserveTokens,
            int maximumReserveTokens,
            int minimumOutputTokens,
            double charsPerToken
    ) {

        private static Defaults defaultValues() {
            return new Defaults(32_768, 0.20d, 0.05d, 1_024, 4_096, 160, 3.2d);
        }

        private Defaults normalized() {
            return new Defaults(
                    contextWindowTokens <= 0 ? 32_768 : contextWindowTokens,
                    safeOutputRatio <= 0 ? 0.20d : Math.min(1.0d, safeOutputRatio),
                    reserveRatio <= 0 ? 0.05d : reserveRatio,
                    minimumReserveTokens <= 0 ? 1_024 : minimumReserveTokens,
                    maximumReserveTokens <= 0 ? 4_096 : Math.max(minimumReserveTokens <= 0 ? 1_024 : minimumReserveTokens, maximumReserveTokens),
                    minimumOutputTokens <= 0 ? 160 : minimumOutputTokens,
                    charsPerToken <= 0 ? 3.2d : charsPerToken
            );
        }
    }

    public record Override(
            Integer contextWindowTokens,
            Double safeOutputRatio,
            Double reserveRatio,
            Integer minimumReserveTokens,
            Integer maximumReserveTokens,
            Integer minimumOutputTokens,
            Double charsPerToken
    ) {
    }
}
