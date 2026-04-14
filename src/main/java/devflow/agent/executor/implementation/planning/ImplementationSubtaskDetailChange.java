package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * planning detail 阶段允许模型输出的最小变更声明。
 *
 * <p>这里故意不承载 editScope、runtimeOwnership 等高层结构语义，
 * 只保留 Claude 风格的最小文件级意图：路径、动作、原因。
 */
public record ImplementationSubtaskDetailChange(
        String path,
        ChangeAction action,
        String reason
) {
}
