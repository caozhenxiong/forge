package devflow.agent.protocol;

import java.util.List;

/**
 * 聚合后的工具结果 machine block。
 *
 * <p>流程层后续应优先读取这份结构化工具执行结果，
 * 而不是重新从 markdown prose 里猜测试/验证状态。
 */
public record ToolResultsArtifactPayload(
        List<ToolResultPayload> toolResults
) {

    public ToolResultsArtifactPayload {
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
