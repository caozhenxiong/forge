package devflow.agent.validation;

import devflow.agent.executor.ToolResult;

/**
 * 单个 validation step 的确定性执行结果。
 */
record ValidationStepExecution(
        ValidationStepResult stepResult,
        ToolResult toolResult
) {
}
