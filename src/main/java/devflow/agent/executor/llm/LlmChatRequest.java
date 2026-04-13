package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * chat provider 的统一请求体。
 *
 * <p>对 text-only 调用和 tool loop 调用都走同一份请求结构，
 * 避免 implementation 再保留第二套 `system + user` 专用接口。
 */
public record LlmChatRequest(
        List<LlmChatMessage> messages,
        List<LlmToolDefinition> tools,
        Map<String, Object> options,
        ModelRole role
) {

    public LlmChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        options = options == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(options));
    }

    public static LlmChatRequest textTurn(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> options,
            ModelRole role
    ) {
        return new LlmChatRequest(
                List.of(
                        LlmChatMessage.system(systemPrompt),
                        LlmChatMessage.user(userPrompt)
                ),
                List.of(),
                options,
                role
        );
    }
}
