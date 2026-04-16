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
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;
import java.util.Locale;

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

    private final ImplementationStateCodec stateCodec;
    private final ImplementationSnapshotRestorer snapshotRestorer;
    private final CompletedPlanPatchOwnerResolver completedPlanPatchOwnerResolver;
    private final RuntimeWiringRetryChangeFactory runtimeWiringRetryChangeFactory;

    public ImplementationResumePolicy(ObjectMapper objectMapper) {
        this.stateCodec = new ImplementationStateCodec(objectMapper);
        this.snapshotRestorer = new ImplementationSnapshotRestorer();
        this.completedPlanPatchOwnerResolver = new CompletedPlanPatchOwnerResolver();
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
            ImplementationStateSnapshot snapshot = stateCodec.readRequired(previousStateJson);
            if (snapshot == null || snapshot.subtasks() == null || snapshot.subtasks().isEmpty()) {
                return null;
            }
            PersistedContinuation continuation = resolveContinuation(
                    snapshot,
                    fixMode,
                    implementationPatchTarget,
                    overrideChanges
            );
            if (continuation.mode().blocked()) {
                return null;
            }
            List<Subtask> subtasks = snapshotRestorer.restoreSubtasks(snapshot.subtasks());
            List<SubtaskExecutionReport> previousReports = snapshotRestorer.restoreReports(snapshot.reports(), subtasks);
            ArchitectIntegrationCheckResult contractGateResult = snapshotRestorer.restoreContractGate(snapshot.contractGate());
            List<FileChange> continuationChanges = canonicalizeContinuationChanges(
                    continuation.patchTarget(),
                    continuation.overrideChanges(),
                    contractGateResult
            );
            if (snapshot.planCompleted()) {
                if (!continuation.mode().patchContinue()) {
                    return null;
                }
                return reopenCompletedPlanPatch(
                        snapshot,
                        subtasks,
                        previousReports,
                        continuation.patchTarget(),
                        contractGateResult,
                        continuationChanges
                );
            }
            List<SubtaskExecutionReport> completedPrefix = snapshotRestorer.takeCompletedPrefix(previousReports);
            if (completedPrefix.size() >= subtasks.size()) {
                return null;
            }
            SubtaskExecutionState resumedExecutionState = completedPrefix.size() < previousReports.size()
                    ? snapshotRestorer.restoreExecutionState(previousReports.get(completedPrefix.size()))
                    : null;
            if (continuation.mode().patchContinue() && resumedExecutionState == null) {
                resumedExecutionState = new SubtaskExecutionState(DeliveryMode.PATCH, true);
            }
            if (resumedExecutionState != null) {
                resumedExecutionState = resumedExecutionState.applyRevisionDirective(
                        buildRetryDirective(continuation.patchTarget(), continuationChanges)
                );
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
            ImplementationPatchTarget persistedPatchTarget,
            ArchitectIntegrationCheckResult contractGateResult,
            List<FileChange> overrideChanges
    ) {
        ImplementationPatchTarget effectivePatchTarget = resolveCompletedPlanPatchTarget(persistedPatchTarget);
        int targetIndex = resolveCompletedPlanTargetIndex(
                effectivePatchTarget,
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

    private int resolveCompletedPlanTargetIndex(
            ImplementationPatchTarget patchTarget,
            List<SubtaskExecutionReport> previousReports,
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (overrideChanges == null || overrideChanges.isEmpty()) {
            throw new IllegalStateException("Completed implementation PATCH requires deterministic target paths.");
        }
        if (patchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING
                && contractGateResult != null
                && contractGateResult.runtimeContract() != null
                && contractGateResult.runtimeContract().active()) {
            return completedPlanPatchOwnerResolver.requireResolvableRuntimeWiringOwnerIndex(
                    previousReports,
                    contractGateResult.runtimeContract()
            );
        }
        return completedPlanPatchOwnerResolver.requireSingleCompletedOwnerIndex(previousReports, overrideChanges);
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

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private PersistedContinuation resolveContinuation(
            ImplementationStateSnapshot snapshot,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            List<FileChange> overrideChanges
    ) {
        PersistedContinuation requested = requestedContinuation(fixMode, implementationPatchTarget, overrideChanges);
        if (requested != null) {
            return requested;
        }
        return restoreContinuation(snapshot);
    }

    private PersistedContinuation requestedContinuation(
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            List<FileChange> overrideChanges
    ) {
        if (fixMode != FixMode.PATCH
                || implementationPatchTarget == null
                || !implementationPatchTarget.concretePatch()
                || overrideChanges == null
                || overrideChanges.isEmpty()) {
            return null;
        }
        return new PersistedContinuation(
                ImplementationContinuationMode.PATCH_CONTINUE,
                implementationPatchTarget,
                List.copyOf(overrideChanges)
        );
    }

    private PersistedContinuation restoreContinuation(ImplementationStateSnapshot snapshot) {
        if (snapshot == null) {
            return PersistedContinuation.none();
        }
        ImplementationContinuationMode mode = parseContinuationMode(snapshot.continuationMode());
        ImplementationPatchTarget patchTarget = parsePatchTarget(snapshot.continuationPatchTarget());
        List<FileChange> overrideChanges = restoreContinuationChanges(snapshot.continuationOverrideChanges());
        if (!mode.patchContinue()) {
            return new PersistedContinuation(mode, ImplementationPatchTarget.NONE, List.of());
        }
        if (!patchTarget.concretePatch() || overrideChanges.isEmpty()) {
            throw new IllegalStateException("Persisted PATCH_CONTINUE requires a canonical repair package.");
        }
        return new PersistedContinuation(mode, patchTarget, overrideChanges);
    }

    private List<FileChange> canonicalizeContinuationChanges(
            ImplementationPatchTarget patchTarget,
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (patchTarget != ImplementationPatchTarget.PATCH_RUNTIME_WIRING
                || contractGateResult == null
                || contractGateResult.runtimeContract() == null
                || !contractGateResult.runtimeContract().active()) {
            return overrideChanges;
        }
        return runtimeWiringRetryChangeFactory.build(contractGateResult.runtimeContract());
    }

    private List<FileChange> restoreContinuationChanges(
            List<ImplementationStateSnapshot.FileChangeState> changes
    ) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        return changes.stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .map(change -> new FileChange(
                        change.path(),
                        parseChangeAction(change.action()),
                        blankIfNull(change.reason()),
                        parseFileEditScope(change.editScope()),
                        parseRuntimeOwnership(change.runtimeOwnership()),
                        change.hostHtmlPatchRequired()
                ))
                .toList();
    }

    private ChangeAction parseChangeAction(String action) {
        if (action == null || action.isBlank()) {
            return ChangeAction.WRITE;
        }
        try {
            return ChangeAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ChangeAction.WRITE;
        }
    }

    private FileEditScope parseFileEditScope(String editScope) {
        if (editScope == null || editScope.isBlank()) {
            return FileEditScope.AUTO;
        }
        try {
            return FileEditScope.valueOf(editScope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return FileEditScope.AUTO;
        }
    }

    private RuntimeOwnershipMode parseRuntimeOwnership(String runtimeOwnership) {
        if (runtimeOwnership == null || runtimeOwnership.isBlank()) {
            return null;
        }
        try {
            return RuntimeOwnershipMode.valueOf(runtimeOwnership.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ImplementationContinuationMode parseContinuationMode(String continuationMode) {
        if (continuationMode == null || continuationMode.isBlank()) {
            return ImplementationContinuationMode.MID_PLAN_CONTINUE;
        }
        try {
            return ImplementationContinuationMode.valueOf(continuationMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ImplementationContinuationMode.MID_PLAN_CONTINUE;
        }
    }

    private ImplementationPatchTarget parsePatchTarget(String patchTarget) {
        if (patchTarget == null || patchTarget.isBlank()) {
            return ImplementationPatchTarget.NONE;
        }
        try {
            return ImplementationPatchTarget.valueOf(patchTarget.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ImplementationPatchTarget.NONE;
        }
    }

    private record PersistedContinuation(
            ImplementationContinuationMode mode,
            ImplementationPatchTarget patchTarget,
            List<FileChange> overrideChanges
    ) {
        private static PersistedContinuation none() {
            return new PersistedContinuation(
                    ImplementationContinuationMode.MID_PLAN_CONTINUE,
                    ImplementationPatchTarget.NONE,
                    List.of()
            );
        }
    }
}
