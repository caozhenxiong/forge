package devflow.agent.executor.implementation;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.executor.runtime.RuntimeWiringRetryChangeFactory;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.SubtaskRevisionDirective;
/**
 * 统一维护 implementation 阶段的状态复用与 PATCH continuation 规则。
 *
 * <p>这层只关心“上一轮状态能不能继续用、怎么恢复成可继续执行的计划”，
 * 不负责真正执行子任务，也不负责决定外层流程如何跳转。
 */
public class ImplementationResumePolicy {

    private final ObjectMapper objectMapper;
    private final ImplementationSnapshotRestorer snapshotRestorer;
    private final RuntimeWiringRetryChangeFactory runtimeWiringRetryChangeFactory;

    public ImplementationResumePolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.snapshotRestorer = new ImplementationSnapshotRestorer();
        this.runtimeWiringRetryChangeFactory = new RuntimeWiringRetryChangeFactory();
    }

    /**
     * PATCH 的正确语义应当是“沿用上一轮已验证的计划继续修复”，而不是重新规划出一条更粗糙的路径。
     * 因此这里不只复用“计划未执行完”的状态，也允许复用“计划已执行完但 contract gate 未通过”的状态。
     */
    public ReusableImplementationState loadReusableImplementationState(
            String previousStateJson,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            List<FileChange> overrideChanges,
            DocumentLanguage language
    ) {
        if (fixMode != FixMode.PATCH || previousStateJson == null || previousStateJson.isBlank()) {
            return null;
        }
        try {
            ImplementationStateSnapshot snapshot = objectMapper.readValue(previousStateJson, ImplementationStateSnapshot.class);
            if (snapshot == null || snapshot.subtasks() == null || snapshot.subtasks().isEmpty()) {
                return null;
            }
            List<Subtask> subtasks = snapshotRestorer.restoreSubtasks(snapshot.subtasks());
            List<SubtaskExecutionReport> previousReports = snapshotRestorer.restoreReports(snapshot.reports(), subtasks);
            ArchitectIntegrationCheckResult contractGateResult = snapshotRestorer.restoreContractGate(snapshot.contractGate());
            if (snapshot.planCompleted()) {
                if (contractGateResult == null || contractGateResult.passed()) {
                    return null;
                }
                return reopenCompletedPlanPatch(
                        snapshot,
                        subtasks,
                        previousReports,
                        implementationPatchTarget,
                        contractGateResult,
                        resolveCompletedPlanOverrideChanges(implementationPatchTarget, overrideChanges, contractGateResult)
                );
            }
            List<SubtaskExecutionReport> completedPrefix = snapshotRestorer.takeCompletedPrefix(previousReports);
            if (completedPrefix.size() >= subtasks.size()) {
                return null;
            }
            SubtaskExecutionState resumedExecutionState = completedPrefix.size() < previousReports.size()
                    ? snapshotRestorer.restoreExecutionState(previousReports.get(completedPrefix.size()))
                    : null;
            if (resumedExecutionState != null) {
                resumedExecutionState = resumedExecutionState.applyRevisionDirective(
                        buildRetryDirective(
                                resolveIncompletePlanPatchTarget(implementationPatchTarget),
                                overrideChanges
                        )
                );
                resumedExecutionState.resetToolLoopTranscript();
            }
            return new ReusableImplementationState(
                    new ImplementationPlan(blankIfNull(snapshot.summary()), subtasks),
                    completedPrefix,
                    resumedExecutionState
            );
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ReusableImplementationState reopenCompletedPlanPatch(
            ImplementationStateSnapshot snapshot,
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            ImplementationPatchTarget requestedPatchTarget,
            ArchitectIntegrationCheckResult contractGateResult,
            List<FileChange> overrideChanges
    ) {
        ImplementationPatchTarget effectivePatchTarget = resolveCompletedPlanPatchTarget(requestedPatchTarget);
        int targetIndex = resolveCompletedPlanTargetIndex(
                effectivePatchTarget,
                subtasks,
                previousReports,
                overrideChanges,
                contractGateResult
        );
        List<SubtaskExecutionReport> completedPrefix = targetIndex <= 0
                ? List.of()
                : List.copyOf(previousReports.subList(0, targetIndex));
        SubtaskExecutionState resumedExecutionState = targetIndex < previousReports.size()
                ? snapshotRestorer.restoreExecutionState(previousReports.get(targetIndex))
                : null;
        if (resumedExecutionState == null) {
            resumedExecutionState = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        }
        resumedExecutionState = resumedExecutionState.applyRevisionDirective(
                buildRetryDirective(effectivePatchTarget, overrideChanges)
        );
        resumedExecutionState.resetToolLoopTranscript();
        return new ReusableImplementationState(
                new ImplementationPlan(blankIfNull(snapshot.summary()), subtasks),
                completedPrefix,
                resumedExecutionState
        );
    }

    private ImplementationPatchTarget resolveCompletedPlanPatchTarget(
            ImplementationPatchTarget requestedPatchTarget
    ) {
        if (requestedPatchTarget != null && requestedPatchTarget.concretePatch()) {
            return requestedPatchTarget;
        }
        throw new IllegalStateException("Completed implementation PATCH requires implementationPatchTarget.");
    }

    private ImplementationPatchTarget resolveIncompletePlanPatchTarget(
            ImplementationPatchTarget requestedPatchTarget
    ) {
        if (requestedPatchTarget != null && requestedPatchTarget.concretePatch()) {
            return requestedPatchTarget;
        }
        return ImplementationPatchTarget.NONE;
    }

    private int resolveCompletedPlanTargetIndex(
            ImplementationPatchTarget patchTarget,
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        Set<Path> targetPaths = targetPaths(overrideChanges);
        if (targetPaths.isEmpty()) {
            throw new IllegalStateException("Completed implementation PATCH requires deterministic target paths.");
        }
        if (patchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return resolveRuntimeWiringTargetIndex(subtasks, previousReports, targetPaths, contractGateResult);
        }
        return findUniqueDeclaredOwnerIndex(subtasks, previousReports, targetPaths);
    }

    private int resolveRuntimeWiringTargetIndex(
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            Set<Path> targetPaths,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        HtmlRuntimeOwnershipContract runtimeContract = contractGateResult == null ? null : contractGateResult.runtimeContract();
        Set<Path> runtimeRootPaths = runtimeRootPaths(runtimeContract);
        int runtimeRootOwnerIndex = findLatestOwnerIndex(subtasks, previousReports, runtimeRootPaths, true);
        if (runtimeRootOwnerIndex >= 0) {
            return runtimeRootOwnerIndex;
        }
        Path htmlEntryPath = runtimeContract == null ? null : runtimeContract.htmlEntryPath();
        if (htmlEntryPath != null) {
            int htmlOwnerIndex = findUniqueOwnerIndex(subtasks, previousReports, Set.of(htmlEntryPath));
            if (htmlOwnerIndex >= 0) {
                return htmlOwnerIndex;
            }
        }
        throw new IllegalStateException("Completed implementation PATCH could not find an owning subtask for the target paths.");
    }

    private int findLatestOwnerIndex(
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            Set<Path> ownerPaths,
            boolean requireAllPaths
    ) {
        if (ownerPaths.isEmpty()) {
            return -1;
        }
        int limit = Math.min(subtasks.size(), previousReports.size());
        for (int index = limit - 1; index >= 0; index--) {
            Subtask subtask = subtasks.get(index);
            SubtaskExecutionReport report = previousReports.get(index);
            if (subtask == null || report == null || !report.completed()) {
                continue;
            }
            Set<Path> declaredOwnerPaths = declaredOwnerPaths(subtask);
            if (ownsCompletedPatch(declaredOwnerPaths, ownerPaths, requireAllPaths)) {
                return index;
            }
        }
        return -1;
    }

    private int findUniqueOwnerIndex(
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            Set<Path> ownerPaths
    ) {
        if (ownerPaths.isEmpty()) {
            return -1;
        }
        Integer matchedIndex = null;
        int limit = Math.min(subtasks.size(), previousReports.size());
        for (int index = 0; index < limit; index++) {
            Subtask subtask = subtasks.get(index);
            SubtaskExecutionReport report = previousReports.get(index);
            if (subtask == null || report == null || !report.completed()) {
                continue;
            }
            Set<Path> declaredOwnerPaths = declaredOwnerPaths(subtask);
            if (!ownsCompletedPatch(declaredOwnerPaths, ownerPaths, true)) {
                continue;
            }
            if (matchedIndex != null) {
                return -1;
            }
            matchedIndex = index;
        }
        return matchedIndex == null ? -1 : matchedIndex;
    }

    private int findUniqueDeclaredOwnerIndex(
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            Set<Path> targetPaths
    ) {
        int ownerIndex = findUniqueOwnerIndex(subtasks, previousReports, targetPaths);
        if (ownerIndex >= 0) {
            return ownerIndex;
        }
        throw new IllegalStateException("Completed implementation PATCH requires overrideChanges owned by a single completed subtask.");
    }

    private Set<Path> runtimeRootPaths(HtmlRuntimeOwnershipContract runtimeContract) {
        if (runtimeContract == null || !runtimeContract.externalCompanion() || !runtimeContract.hasResolvedWiringRepairScope()) {
            return Set.of();
        }
        LinkedHashSet<Path> runtimeRootPaths = new LinkedHashSet<>(runtimeContract.runtimePaths());
        return runtimeRootPaths.isEmpty() ? Set.of() : Set.copyOf(runtimeRootPaths);
    }

    private Set<Path> declaredOwnerPaths(Subtask subtask) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return Set.of();
        }
        return targetPaths(subtask.changes());
    }

    private boolean ownsCompletedPatch(
            Set<Path> effectivePaths,
            Set<Path> ownerPaths,
            boolean requireAllPaths
    ) {
        if (effectivePaths.isEmpty()) {
            return false;
        }
        if (requireAllPaths) {
            return effectivePaths.containsAll(ownerPaths);
        }
        for (Path targetPath : ownerPaths) {
            if (effectivePaths.contains(targetPath)) {
                return true;
            }
        }
        return false;
    }

    private Set<Path> targetPaths(
            List<FileChange> overrideChanges
    ) {
        LinkedHashSet<Path> targetPaths = new LinkedHashSet<>();
        if (overrideChanges != null) {
            for (FileChange change : overrideChanges) {
                if (change == null || change.path() == null || change.path().isBlank()) {
                    continue;
                }
                targetPaths.add(Path.of(change.path()).normalize());
            }
        }
        return Set.copyOf(targetPaths);
    }

    private SubtaskRevisionDirective buildRetryDirective(
            ImplementationPatchTarget patchTarget,
            List<FileChange> overrideChanges
    ) {
        if (patchTarget == null || patchTarget == ImplementationPatchTarget.NONE) {
            return SubtaskRevisionDirective.empty();
        }
        if (overrideChanges == null || overrideChanges.isEmpty()) {
            throw new IllegalStateException(patchTarget.name() + " continuation requires overrideChanges.");
        }
        return SubtaskRevisionDirective.patch(List.copyOf(overrideChanges));
    }

    private List<FileChange> resolveCompletedPlanOverrideChanges(
            ImplementationPatchTarget patchTarget,
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (patchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            HtmlRuntimeOwnershipContract runtimeContract = contractGateResult == null ? null : contractGateResult.runtimeContract();
            if (runtimeContract == null || !runtimeContract.hasResolvedWiringRepairScope()) {
                throw new IllegalStateException("Completed runtime wiring PATCH requires a resolved runtime contract.");
            }
            return runtimeWiringRetryChangeFactory.build(runtimeContract);
        }
        return overrideChanges == null ? List.of() : List.copyOf(overrideChanges);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
