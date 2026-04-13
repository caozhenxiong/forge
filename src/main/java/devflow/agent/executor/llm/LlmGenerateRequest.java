package devflow.agent.executor.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * generate 主链的唯一请求载体。
 */
public record LlmGenerateRequest(
        String systemPrompt,
        LlmPromptContext promptContext,
        Map<String, Object> options,
        ModelRole role
) {

    public LlmGenerateRequest {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        promptContext = promptContext == null ? LlmPromptContext.empty() : promptContext;
        options = options == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(options));
    }

    public static LlmGenerateRequest workingPrompt(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> options,
            ModelRole role
    ) {
        return new LlmGenerateRequest(systemPrompt, LlmPromptContext.workingOnly(userPrompt), options, role);
    }

    public String userPrompt() {
        return promptContext.render();
    }
}
