package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.util.List;
import java.util.stream.Stream;

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
            QualityPlan qualityPlan,
            ImplementationContinuationConstraints continuationConstraints,
            ImplementationPlan plan
    ) {
        return new ImplementationPlanGateInput(
                fingerprint,
                contractView,
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
                continuationConstraints,
                plan.subtasks()
        );
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
