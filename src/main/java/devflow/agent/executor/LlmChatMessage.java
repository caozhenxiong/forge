package devflow.agent.executor;

import java.util.List;

/**
 * 统一描述 provider 交互中的消息块。
 *
 * <p>Forge 当前只需要 Claude-style tool loop 的最小消息语义：
 * 1. 文本 content；
 * 2. assistant tool calls；
 * 3. tool result 的 toolName/toolCallId 绑定。
 */
public record LlmChatMessage(
        LlmChatRole role,
        String content,
        String toolName,
        String toolCallId,
        List<LlmToolCall> toolCalls
) {

    public LlmChatMessage {
        role = role == null ? LlmChatRole.USER : role;
        content = content == null ? "" : content;
        toolName = toolName == null ? "" : toolName;
        toolCallId = toolCallId == null ? "" : toolCallId;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LlmChatMessage system(String content) {
        return new LlmChatMessage(LlmChatRole.SYSTEM, content, "", "", List.of());
    }

    public static LlmChatMessage user(String content) {
        return new LlmChatMessage(LlmChatRole.USER, content, "", "", List.of());
    }

    public static LlmChatMessage assistant(String content) {
        return new LlmChatMessage(LlmChatRole.ASSISTANT, content, "", "", List.of());
    }

    public static LlmChatMessage assistantToolCalls(String content, List<LlmToolCall> toolCalls) {
        return new LlmChatMessage(LlmChatRole.ASSISTANT, content, "", "", toolCalls);
    }

    public static LlmChatMessage toolResult(String toolName, String toolCallId, String content) {
        return new LlmChatMessage(LlmChatRole.TOOL, content, toolName, toolCallId, List.of());
    }
}
