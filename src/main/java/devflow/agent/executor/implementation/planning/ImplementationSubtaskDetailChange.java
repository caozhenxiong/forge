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
 * <p>这里不承载 editScope、runtimeOwnership 等执行层语义，
 * 但允许对新增 runtime 脚本声明最小 planning 角色，避免 gate 再从路径猜 root/leaf。
 */
public record ImplementationSubtaskDetailChange(
        String path,
        ChangeAction action,
        String reason,
        PlanningRuntimeScriptRole runtimeScriptRole
) {
    public ImplementationSubtaskDetailChange(String path, ChangeAction action, String reason) {
        this(path, action, reason, null);
    }
}
