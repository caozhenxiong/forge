package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一的工具执行结果。
 *
 * <p>这层现在已经覆盖：
 * 1. patch apply / 本地 parse/verify；
 * 2. 自检验证（command / resource / syntax / smoke）；
 * 3. testcase 执行与 runtime snapshot。
 *
 * <p>后续继续扩展时，也必须优先补结构化 failure code，
 * 不能再退回到 message-based routing。
 */
public record ToolResult(
        ToolName toolName,
        ToolStatus status,
        ToolFailureCode failureCode,
        String evidence,
        String recommendedNextAction
) {
    public static ToolResult success(ToolName toolName) {
        return new ToolResult(toolName, ToolStatus.SUCCEEDED, null, null, null);
    }

    public static ToolResult skipped(
            ToolName toolName,
            String evidence,
            String recommendedNextAction
    ) {
        return new ToolResult(toolName, ToolStatus.SKIPPED, null, evidence, recommendedNextAction);
    }

    public static ToolResult failure(
            ToolName toolName,
            ToolFailureCode failureCode,
            String evidence,
            String recommendedNextAction
    ) {
        return new ToolResult(toolName, ToolStatus.FAILED, failureCode, evidence, recommendedNextAction);
    }

    public boolean succeeded() {
        return status == ToolStatus.SUCCEEDED;
    }
}
