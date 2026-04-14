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

import java.util.List;

/**
 * 单个子任务的 detail 产物。
 *
 * <p>detail 只补齐当前子任务自己的文件变更声明，不允许越界改其他子任务拥有的目标文件。
 */
public record ImplementationSubtaskDetail(
        String subtaskId,
        List<ImplementationSubtaskDetailChange> changes
) {
}
