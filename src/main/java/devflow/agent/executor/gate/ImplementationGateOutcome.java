package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

import devflow.agent.executor.subtask.SubtaskExecutionReport;
/**
 * implementation 阶段整体 gate 的统一输出。
 *
 * <p>这层只描述阶段级检查后的稳定结果，不直接关心 markdown 渲染、
 * artifact 落盘或外层流程如何继续。
 */
public record ImplementationGateOutcome(
        List<SubtaskExecutionReport> reports,
        ImplementationStageStatus stageStatus,
        ArchitectIntegrationCheckResult contractGateResult
) {
}
