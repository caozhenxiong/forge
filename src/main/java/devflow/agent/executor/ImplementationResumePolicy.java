package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
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
class ImplementationResumePolicy {

    private final ObjectMapper objectMapper;
    private final ImplementationSnapshotRestorer snapshotRestorer;
    private final RuntimeWiringRetryChangeFactory runtimeWiringRetryChangeFactory;

    ImplementationResumePolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.snapshotRestorer = new ImplementationSnapshotRestorer();
        this.runtimeWiringRetryChangeFactory = new RuntimeWiringRetryChangeFactory();
    }

    /**
     * PATCH 的正确语义应当是“沿用上一轮已验证的计划继续修复”，而不是重新规划出一条更粗糙的路径。
     * 因此这里不只复用“计划未执行完”的状态，也允许复用“计划已执行完但 contract gate 未通过”的状态。
     */
    ReusableImplementationState loadReusableImplementationState(
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
                        overrideChanges,
                        contractGateResult
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
                                resolveIncompletePlanPatchTarget(implementationPatchTarget, contractGateResult),
                                overrideChanges,
                                contractGateResult,
                                subtasks.get(completedPrefix.size())
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
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        ImplementationPatchTarget effectivePatchTarget = resolveCompletedPlanPatchTarget(contractGateResult, requestedPatchTarget);
        int targetIndex = resolveCompletedPlanTargetIndex(subtasks, previousReports, overrideChanges, contractGateResult);
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
                buildRetryDirective(effectivePatchTarget, overrideChanges, contractGateResult, subtasks.get(targetIndex))
        );
        resumedExecutionState.resetToolLoopTranscript();
        return new ReusableImplementationState(
                new ImplementationPlan(blankIfNull(snapshot.summary()), subtasks),
                completedPrefix,
                resumedExecutionState
        );
    }

    private ImplementationPatchTarget resolveCompletedPlanPatchTarget(
            ArchitectIntegrationCheckResult contractGateResult,
            ImplementationPatchTarget requestedPatchTarget
    ) {
        if (requestedPatchTarget != null && requestedPatchTarget.concretePatch()) {
            return requestedPatchTarget;
        }
        if (contractGateResult != null && contractGateResult.implementationPatchTarget() != null
                && contractGateResult.implementationPatchTarget().concretePatch()) {
            return contractGateResult.implementationPatchTarget();
        }
        throw new IllegalStateException("Completed implementation PATCH requires implementationPatchTarget.");
    }

    private ImplementationPatchTarget resolveIncompletePlanPatchTarget(
            ImplementationPatchTarget requestedPatchTarget,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (requestedPatchTarget != null && requestedPatchTarget.concretePatch()) {
            return requestedPatchTarget;
        }
        if (contractGateResult != null
                && contractGateResult.scope() == ArchitectIntegrationCheckScope.RUNNABLE_MILESTONE
                && contractGateResult.implementationPatchTarget() != null
                && contractGateResult.implementationPatchTarget().concretePatch()) {
            return contractGateResult.implementationPatchTarget();
        }
        return ImplementationPatchTarget.NONE;
    }

    private int resolveCompletedPlanTargetIndex(
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        Set<Path> targetPaths = targetPaths(overrideChanges, contractGateResult);
        if (targetPaths.isEmpty()) {
            throw new IllegalStateException("Completed implementation PATCH requires deterministic target paths.");
        }
        int limit = Math.min(subtasks.size(), previousReports.size());
        for (int index = limit - 1; index >= 0; index--) {
            Subtask subtask = subtasks.get(index);
            SubtaskExecutionReport report = previousReports.get(index);
            if (subtask == null || report == null || !report.completed()) {
                continue;
            }
            List<FileChange> effectiveChanges = report.effectiveChanges().isEmpty()
                    ? (subtask.changes() == null ? List.of() : subtask.changes())
                    : report.effectiveChanges();
            boolean ownsTargetPath = effectiveChanges.stream()
                    .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                    .map(change -> Path.of(change.path()).normalize())
                    .anyMatch(targetPaths::contains);
            if (ownsTargetPath) {
                return index;
            }
        }
        throw new IllegalStateException("Completed implementation PATCH could not find an owning subtask for the target paths.");
    }

    private Set<Path> targetPaths(
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult
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
        HtmlRuntimeOwnershipContract runtimeContract = contractGateResult == null ? null : contractGateResult.runtimeContract();
        if (runtimeContract != null && runtimeContract.active()) {
            if (runtimeContract.htmlEntryPath() != null) {
                targetPaths.add(runtimeContract.htmlEntryPath().normalize());
            }
            for (Path runtimePath : runtimeContract.runtimePaths()) {
                if (runtimePath != null) {
                    targetPaths.add(runtimePath.normalize());
                }
            }
        }
        return Set.copyOf(targetPaths);
    }

    private SubtaskRevisionDirective buildRetryDirective(
            ImplementationPatchTarget patchTarget,
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult,
            Subtask subtask
    ) {
        if (patchTarget == null || patchTarget == ImplementationPatchTarget.NONE) {
            return SubtaskRevisionDirective.empty();
        }
        if (patchTarget == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION) {
            if (overrideChanges == null || overrideChanges.isEmpty()) {
                throw new IllegalStateException("PATCH_EXISTING_IMPLEMENTATION continuation requires overrideChanges.");
            }
            return SubtaskRevisionDirective.patch(List.copyOf(overrideChanges));
        }
        HtmlRuntimeOwnershipContract runtimeContract = contractGateResult == null ? null : contractGateResult.runtimeContract();
        if (runtimeContract == null || !runtimeContract.active() || runtimeContract.htmlEntryPath() == null) {
            throw new IllegalStateException("Runtime wiring continuation requires a resolved runtime contract.");
        }
        return SubtaskRevisionDirective.patch(runtimeWiringRetryChangeFactory.build(runtimeContract));
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
