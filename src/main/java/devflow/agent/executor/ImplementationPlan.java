package devflow.agent.executor;

import java.util.List;

/**
 * 表示一次 implementation 规划的稳定结果。
 * 它只描述“计划是什么”，不负责记录执行过程。
 */
record ImplementationPlan(
        String summary,
        List<Subtask> subtasks
) {
}
