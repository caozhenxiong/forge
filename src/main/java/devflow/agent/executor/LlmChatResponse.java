package devflow.agent.executor;

import java.util.List;

/**
 * chat provider 的统一响应。
 *
 * <p>只保留 coding runtime 需要消费的稳定输出：
 * 1. assistant 文本；
 * 2. tool calls；
 * 3. telemetry；
 * 4. done reason。
 */
public record LlmChatResponse(
        String content,
        List<LlmToolCall> toolCalls,
        GenerationTelemetry telemetry,
        String doneReason
) {

    public LlmChatResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        doneReason = doneReason == null ? "" : doneReason;
    }
}
