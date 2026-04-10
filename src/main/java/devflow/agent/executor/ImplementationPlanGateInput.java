package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.util.List;

/**
 * implementation 计划 gate 的输入。
 *
 * <p>先把 planning coverage 相关的上下文收成一个稳定对象，后续再接 milestone/edit/test gate 时，
 * 可以保持同样的“先聚合输入，再交给 gate”模式。
 */
record ImplementationPlanGateInput(
        ProjectFingerprint fingerprint,
        ContractView contractView,
        List<String> plannedPaths,
        List<String> plannedCoverageItems,
        List<String> plannedCoverageRefs,
        List<String> plannedDeliveryModes,
        boolean hasRunnableMilestone,
        boolean hasNonSkeletonRunnableMilestone,
        QualityPlan qualityPlan,
        ImplementationContinuationConstraints continuationConstraints,
        List<Subtask> subtasks
) {
}
