package devflow.agent.executor.implementation.state;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import java.util.List;

import devflow.agent.executor.implementation.ImplementationEventEntry;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * implementation 阶段的单一状态源。
 * 所有中间 markdown/json 产物都应从这份快照派生，避免 implementation.md、
 * worker_results、progress 和 state.json 各自维护不同的“当前真相”。
 */
public record ImplementationRuntimeSnapshot(
        ImplementationPlan plan,
        List<TaskPackage> taskPackages,
        List<SubtaskExecutionReport> reports,
        List<WorkerResult> workerResults,
        List<ImplementationEventEntry> events,
        String note,
        DocumentLanguage language,
        DeliveryPolicyEnvelope deliveryPolicy,
        SharedContextBundle sharedContextBundle,
        ImplementationStageStatus stageStatus,
        String currentSubtaskTitle
) {
}
