package devflow.agent.executor;

import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.quality.QualityCoverageRefCatalog;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.util.ProjectPathSupport;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责 implementation plan 的结构校验与标准化。
 *
 * <p>这里集中处理：
 * 1. subtask schema 校验；
 * 2. coverage/capability 清洗；
 * 3. deliveryMode 推导；
 * 4. runnable milestone 补全。
 */
final class ImplementationPlanNormalizationSupport {

    ImplementationPlan normalize(
            ImplementationPlan plan,
            FixMode fixMode,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy,
            ProductContract productContract,
            ExecutionContract executionContract,
            QualityPlan qualityPlan,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        if (plan.subtasks() == null || plan.subtasks().isEmpty()) {
            throw new IllegalStateException("Implementation plan must contain subtasks");
        }
        List<Subtask> normalized = new ArrayList<>();
        for (int index = 0; index < plan.subtasks().size(); index++) {
            Subtask subtask = plan.subtasks().get(index);
            if (subtask.changes() == null || subtask.changes().isEmpty()) {
                throw new ImplementationPlanningException(
                        ImplementationPlanningFailureReason.INVALID_PLAN_SCHEMA,
                        "Each subtask must contain at least one file change"
                );
            }
            if (subtask.changes().size() > deliveryPolicy.maxFiles()) {
                throw new ImplementationPlanningException(
                        ImplementationPlanningFailureReason.INVALID_PLAN_SCHEMA,
                        "Each subtask may change at most %d files".formatted(deliveryPolicy.maxFiles())
                );
            }
            List<FileChange> normalizedChanges = sanitizeStandaloneAssetScopes(subtask.changes());
            DeliveryMode deliveryMode = resolveDeliveryMode(
                    subtask.deliveryMode(),
                    fixMode,
                    preferSkeletonFlow,
                    index,
                    normalizedChanges,
                    deliveryPolicy,
                    executionContract,
                    continuationConstraints
            );
            normalized.add(new Subtask(
                    subtask.title(),
                    subtask.goal(),
                    sanitizeCoverageRefs(subtask.coverageRefs(), productContract, qualityPlan),
                    sanitizeCapabilities(subtask.ownedCapabilities(), subtask.acceptanceCriteria()),
                    sanitizeCapabilities(subtask.deferredCapabilities(), List.of()),
                    subtask.acceptanceCriteria(),
                    subtask.runnableMilestone(),
                    deliveryMode,
                    normalizedChanges
            ));
        }
        return new ImplementationPlan(plan.summary(), normalizeRunnableMilestones(normalized, executionContract));
    }

    private List<Subtask> normalizeRunnableMilestones(List<Subtask> subtasks, ExecutionContract executionContract) {
        if (subtasks == null || subtasks.isEmpty() || executionContract == null) {
            return subtasks;
        }
        if (!requiresExecutionProgression(executionContract)) {
            return subtasks;
        }
        if (subtasks.stream().anyMatch(Subtask::runnableMilestone)) {
            return subtasks;
        }
        int inferredIndex = -1;
        for (int index = 0; index < subtasks.size(); index++) {
            if (targetsExecutionSurfaceFiles(subtasks.get(index).changes(), executionContract)) {
                inferredIndex = index;
                break;
            }
        }
        if (inferredIndex < 0) {
            return subtasks;
        }
        List<Subtask> normalized = new ArrayList<>(subtasks.size());
        for (int index = 0; index < subtasks.size(); index++) {
            Subtask subtask = subtasks.get(index);
            normalized.add(new Subtask(
                    subtask.title(),
                    subtask.goal(),
                    subtask.coverageRefs(),
                    subtask.ownedCapabilities(),
                    subtask.deferredCapabilities(),
                    subtask.acceptanceCriteria(),
                    index == inferredIndex,
                    subtask.deliveryMode(),
                    subtask.changes()
            ));
        }
        return normalized;
    }

    private List<String> sanitizeCoverageRefs(List<String> values, ProductContract productContract, QualityPlan qualityPlan) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .filter(value -> supportsCoverageRef(value, productContract, qualityPlan))
                .distinct()
                .toList();
    }

    private boolean supportsCoverageRef(String value, ProductContract productContract, QualityPlan qualityPlan) {
        if (productContract != null && productContract.containsBindingRequirementId(value)) {
            return true;
        }
        String qualityCapabilityId = QualityCoverageRefCatalog.fromReferenceId(value);
        return !qualityCapabilityId.isBlank()
                && qualityPlan != null
                && qualityPlan.capabilityMatrix().entries().stream()
                .map(entry -> entry == null ? "" : entry.capabilityId())
                .anyMatch(qualityCapabilityId::equals);
    }

    private List<String> sanitizeCapabilities(List<String> values, List<String> fallback) {
        List<String> source = values == null || values.isEmpty() ? fallback : values;
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private DeliveryMode resolveDeliveryMode(
            DeliveryMode rawMode,
            FixMode fixMode,
            boolean preferSkeletonFlow,
            int subtaskIndex,
            List<FileChange> changes,
            DeliveryPolicyEnvelope deliveryPolicy,
            ExecutionContract executionContract,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        DeliveryMode candidate;
        if (deliveryPolicy.mode() != null && deliveryPolicy.mode() != DeliveryMode.INCREMENTAL && rawMode == null) {
            candidate = deliveryPolicy.mode();
        } else if (rawMode != null) {
            candidate = rawMode;
        } else if (fixMode == FixMode.PATCH) {
            candidate = DeliveryMode.PATCH;
        } else if (fixMode == FixMode.REWORK) {
            candidate = DeliveryMode.REWORK;
        } else if (preferSkeletonFlow && subtaskIndex == 0 && targetsExecutionSurfaceFiles(changes, executionContract)) {
            candidate = DeliveryMode.SKELETON;
        } else {
            candidate = DeliveryMode.INCREMENTAL;
        }
        if (rawMode == null
                && candidate == DeliveryMode.SKELETON
                && continuationConstraints != null
                && continuationConstraints.active()
                && touchesExistingContinuationFile(changes, continuationConstraints)) {
            return DeliveryMode.INCREMENTAL;
        }
        return candidate;
    }

    private boolean targetsExecutionSurfaceFiles(List<FileChange> changes, ExecutionContract executionContract) {
        if (changes == null || changes.isEmpty()) {
            return false;
        }
        devflow.agent.context.ExecutionEntryKind entryKind = executionContract == null
                ? devflow.agent.context.ExecutionEntryKind.UNSPECIFIED
                : executionContract.normalizedEntryKindEnum();
        for (FileChange change : changes) {
            String path = blankIfNull(change.path()).toLowerCase();
            if (entryKind.matchesProjectPath(path)) {
                return true;
            }
            if (executionContract != null && executionContract.surfaceRequired() && ProjectPathSupport.isStyle(path)) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresExecutionProgression(ExecutionContract executionContract) {
        if (executionContract == null) {
            return false;
        }
        return executionContract.entryRequired()
                || executionContract.launchRequired()
                || executionContract.surfaceRequired()
                || (executionContract.acceptanceSignals() != null && !executionContract.acceptanceSignals().isEmpty());
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private boolean touchesExistingContinuationFile(
            List<FileChange> changes,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        if (changes == null || changes.isEmpty() || continuationConstraints == null || !continuationConstraints.active()) {
            return false;
        }
        return changes.stream()
                .map(FileChange::path)
                .anyMatch(continuationConstraints::marksExistingPath);
    }

    private List<FileChange> sanitizeStandaloneAssetScopes(List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        List<FileChange> normalized = new ArrayList<>(changes.size());
        for (FileChange change : changes) {
            if (change == null) {
                continue;
            }
            FileEditScope scope = change.effectiveEditScope();
            if (scope == FileEditScope.INLINE_SCRIPT_PATCH && ProjectPathSupport.isRuntimeScript(change.path())) {
                normalized.add(new FileChange(change.path(), change.action(), change.reason(), FileEditScope.AUTO));
                continue;
            }
            if (scope == FileEditScope.INLINE_STYLE_PATCH && ProjectPathSupport.isStyle(change.path())) {
                normalized.add(new FileChange(change.path(), change.action(), change.reason(), FileEditScope.AUTO));
                continue;
            }
            normalized.add(change);
        }
        return List.copyOf(normalized);
    }

}
