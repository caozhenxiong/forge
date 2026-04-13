package devflow.agent.executor;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import java.util.List;

/**
 * implementation 阶段的单一状态源。
 * 所有中间 markdown/json 产物都应从这份快照派生，避免 implementation.md、
 * worker_results、progress 和 state.json 各自维护不同的“当前真相”。
 */
record ImplementationRuntimeSnapshot(
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
