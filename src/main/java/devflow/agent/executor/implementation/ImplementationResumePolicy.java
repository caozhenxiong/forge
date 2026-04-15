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
import java.util.List;

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
    private final CompletedPlanPatchOwnerResolver completedPlanPatchOwnerResolver;
    private final RuntimeWiringRetryChangeFactory runtimeWiringRetryChangeFactory;

    public ImplementationResumePolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            List<SubtaskExecutionReport> previousReports,
            List<FileChange> overrideChanges,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (overrideChanges == null || overrideChanges.isEmpty()) {
            throw new IllegalStateException("Completed implementation PATCH requires deterministic target paths.");
        }
        if (patchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return completedPlanPatchOwnerResolver.requireResolvableRuntimeWiringOwnerIndex(
                    previousReports,
                    contractGateResult == null ? null : contractGateResult.runtimeContract()
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
