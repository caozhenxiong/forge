package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

import devflow.agent.executor.subtask.Subtask;
/**
 * 表示一次 implementation 规划的稳定结果。
 * 它只描述“计划是什么”，不负责记录执行过程。
 */
public record ImplementationPlan(
        String summary,
        List<Subtask> subtasks
) {
}
