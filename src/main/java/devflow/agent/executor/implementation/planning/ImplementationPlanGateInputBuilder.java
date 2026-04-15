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
import java.util.stream.Stream;

import devflow.agent.executor.subtask.Subtask;
/**
 * implementation plan gate 输入装配器。
 *
 * <p>规划结果进入 gate 前，需要把 subtasks 展开成 coverage、文件列表、
 * delivery mode 与 runnable milestone 这些稳定字段；这层负责统一装配，
 * 避免 planner 门面继续内联长链式 stream。
 */
final class ImplementationPlanGateInputBuilder {

    ImplementationPlanGateInput build(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            PlanningRuntimeFacts runtimeFacts,
            QualityPlan qualityPlan,
            ImplementationPatchTarget implementationPatchTarget,
            ImplementationContinuationConstraints continuationConstraints,
            List<ImplementationSubtaskDetail> acceptedDetails,
            ImplementationPlan plan
    ) {
        return new ImplementationPlanGateInput(
                fingerprint,
                contractView,
                runtimeFacts == null ? PlanningRuntimeFacts.empty() : runtimeFacts,
                plan.subtasks().stream()
                        .flatMap(subtask -> subtask.changes().stream())
                        .map(FileChange::path)
                        .toList(),
                plan.subtasks().stream()
                        .flatMap(subtask -> Stream.of(
                                subtask.title(),
                                subtask.goal(),
                                String.join("；", safeList(subtask.coverageRefs())),
                                String.join("；", safeList(subtask.ownedCapabilities())),
                                String.join("；", safeList(subtask.deferredCapabilities())),
                                String.join("；", safeList(subtask.acceptanceCriteria()))
                        ))
                        .toList(),
                plan.subtasks().stream()
                        .flatMap(subtask -> safeList(subtask.coverageRefs()).stream())
                        .toList(),
                plan.subtasks().stream()
                        .map(subtask -> subtask.deliveryMode().name())
                        .toList(),
                plan.subtasks().stream().anyMatch(Subtask::runnableMilestone),
                plan.subtasks().stream().anyMatch(subtask ->
                        subtask.runnableMilestone() && subtask.deliveryMode() != DeliveryMode.SKELETON),
                qualityPlan,
                implementationPatchTarget,
                continuationConstraints,
                acceptedDetails == null ? List.of() : List.copyOf(acceptedDetails),
                plan.subtasks()
        );
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
