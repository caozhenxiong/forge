package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;

import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;
import java.util.List;

import devflow.agent.executor.subtask.Subtask;
/**
 * implementation 计划 gate 的输入。
 *
 * <p>先把 planning coverage 相关的上下文收成一个稳定对象，后续再接 milestone/edit/test gate 时，
 * 可以保持同样的“先聚合输入，再交给 gate”模式。
 */
public record ImplementationPlanGateInput(
        ProjectFingerprint fingerprint,
        ContractView contractView,
        PlanningRuntimeFacts runtimeFacts,
        List<String> plannedPaths,
        List<String> plannedCoverageItems,
        List<String> plannedCoverageRefs,
        List<String> plannedDeliveryModes,
        boolean hasRunnableMilestone,
        boolean hasNonSkeletonRunnableMilestone,
        QualityPlan qualityPlan,
        ImplementationPatchTarget implementationPatchTarget,
        ImplementationContinuationConstraints continuationConstraints,
        List<Subtask> subtasks
) {
}
