package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

/**
 * 子任务完整性 gate 的统一输出。
 *
 * <p>inspection 保留原始完整性检查细节，供 reviewer prompt、retry feedback 和 artifact 渲染继续使用；
 * report 则只负责告诉流程层：当前这一轮到底该不该阻塞。
 */
public record ImplementationCompletenessGateOutcome(
        ImplementationCompletenessResult inspection,
        GateReport report
) {
}
