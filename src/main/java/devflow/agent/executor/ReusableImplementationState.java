package devflow.agent.executor;

import java.util.List;

/**
 * 表示 PATCH 模式下可复用的实现状态。
 * 这里只保存已经确认完成的前缀结果，避免后续重试从头再来。
 */
record ReusableImplementationState(
        ImplementationPlan plan,
        List<SubtaskExecutionReport> completedReports,
        SubtaskExecutionState resumedExecutionState
) {
}
