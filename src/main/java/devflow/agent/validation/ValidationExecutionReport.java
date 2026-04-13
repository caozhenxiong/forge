package devflow.agent.validation;

import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.SelfCheckResult;
import java.util.List;

/**
 * 自检阶段的结构化执行结果。
 *
 * <p>这层把原来的文本汇总结果和工具级结果一起保留下来：
 * 1. `SelfCheckResult` 继续供现有流程使用；
 * 2. `toolResults` 供后续 `tool-result-first` 的流程控制直接消费。
 */
public record ValidationExecutionReport(
        SelfCheckResult selfCheckResult,
        List<ToolResult> toolResults
) {
}
