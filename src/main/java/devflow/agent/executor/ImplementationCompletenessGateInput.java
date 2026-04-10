package devflow.agent.executor;

import devflow.agent.quality.QualityPlan;
import java.nio.file.Path;

/**
 * 子任务完整性 gate 的输入。
 *
 * <p>这里显式带上 finalSubtask，是因为“是否阻塞当前子任务”不只取决于检测结果，
 * 还取决于当前阶段责任边界：同一个占位实现，在中间子任务里可能只是 deferred warning，
 * 但在最终子任务里必须升级成阻塞问题。
 */
record ImplementationCompletenessGateInput(
        Path projectPath,
        Subtask subtask,
        QualityPlan qualityPlan,
        boolean finalSubtask
) {
}
