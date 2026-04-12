package devflow.agent.executor;

import java.util.List;

/**
 * 单个子任务的 detail 产物。
 *
 * <p>detail 只补齐当前子任务自己的文件变更声明，不允许越界改其他子任务拥有的目标文件。
 */
record ImplementationSubtaskDetail(
        String subtaskId,
        List<FileChange> changes
) {
}
