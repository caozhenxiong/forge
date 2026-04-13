package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.SubtaskExecutionState;
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
