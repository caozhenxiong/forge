package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * planning detail 阶段允许模型输出的最小变更声明。
 *
 * <p>这里故意不承载 editScope、runtimeOwnership 等高层结构语义，
 * 只保留 Claude 风格的最小文件级意图：路径、动作、原因。
 */
record ImplementationSubtaskDetailChange(
        String path,
        ChangeAction action,
        String reason
) {
}
