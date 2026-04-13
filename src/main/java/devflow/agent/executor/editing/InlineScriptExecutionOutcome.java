package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 内联脚本 patch 执行结果。
 *
 * <p>正常情况下只返回最终脚本内容；如果在最小单元上仍然稳定触发
 * 结构性失败，则标记为需要执行宿主外提策略。
 */
public record InlineScriptExecutionOutcome(
        String scriptContent,
        boolean externalizeToFile
) {
}
