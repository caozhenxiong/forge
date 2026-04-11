package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.util.EnumParsers;
import java.util.List;

/**
 * 统一维护 implementation 阶段的状态复用与 PATCH continuation 规则。
 *
 * <p>这层只关心“上一轮状态能不能继续用、怎么恢复成可继续执行的计划”，
 * 不负责真正执行子任务，也不负责决定外层流程如何跳转。
 */
class ImplementationResumePolicy {

    private final ObjectMapper objectMapper;
    private final ImplementationSnapshotRestorer snapshotRestorer;
    private final ImplementationPatchContinuationPlanner continuationPlanner;
    private final ImplementationRuntimeContractResolver runtimeContractResolver;

    ImplementationResumePolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.snapshotRestorer = new ImplementationSnapshotRestorer();
        this.continuationPlanner = new ImplementationPatchContinuationPlanner();
        this.runtimeContractResolver = new ImplementationRuntimeContractResolver();
    }

    /**
     * PATCH 的正确语义应当是“沿用上一轮已验证的计划继续修复”，而不是重新规划出一条更粗糙的路径。
     * 因此这里不只复用“计划未执行完”的状态，也允许复用“计划已执行完但 architect 整体检查未通过”的状态。
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
            HtmlRuntimeOwnershipContract runtimeContract = runtimeContractResolver.resolve(snapshot);
            if (!snapshot.architectCheckPassed()) {
                if (!snapshot.planCompleted()) {
                    return null;
                }
                ImplementationPatchTarget effectivePatchTarget =
                        resolveCompletedPlanPatchTarget(snapshot, implementationPatchTarget);
                return continuationPlanner.build(
                        snapshot,
                        subtasks,
                        previousReports,
                        language,
                        effectivePatchTarget,
                        overrideChanges,
                        runtimeContract
                );
            }
            if (snapshot.planCompleted()) {
                if (implementationPatchTarget == null || !implementationPatchTarget.concretePatch()) {
                    throw new IllegalStateException("Completed implementation PATCH requires implementationPatchTarget.");
                }
                ReusableImplementationState reusableState = continuationPlanner.build(
                        snapshot,
                        subtasks,
                        previousReports,
                        language,
                        implementationPatchTarget,
                        overrideChanges,
                        runtimeContract
                );
                if (reusableState != null) {
                    return reusableState;
                }
                return null;
            }
            List<SubtaskExecutionReport> completedPrefix = snapshotRestorer.takeCompletedPrefix(previousReports);
            if (completedPrefix.size() >= subtasks.size()) {
                return null;
            }
            SubtaskExecutionState resumedExecutionState = completedPrefix.size() < previousReports.size()
                    ? snapshotRestorer.restoreExecutionState(previousReports.get(completedPrefix.size()))
                    : null;
            return new ReusableImplementationState(
                    new ImplementationPlan(snapshot.summary() == null ? "" : snapshot.summary(), subtasks),
                    completedPrefix,
                    resumedExecutionState
            );
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ImplementationPatchTarget resolveCompletedPlanPatchTarget(
            ImplementationStateSnapshot snapshot,
            ImplementationPatchTarget requestedPatchTarget
    ) {
        if (requestedPatchTarget != null && requestedPatchTarget.concretePatch()) {
            return requestedPatchTarget;
        }
        ImplementationPatchTarget snapshotPatchTarget = EnumParsers.parseIgnoreCase(
                ImplementationPatchTarget.class,
                snapshot == null ? null : snapshot.architectImplementationPatchTarget(),
                ImplementationPatchTarget.NONE
        );
        if (snapshotPatchTarget.concretePatch()) {
            return snapshotPatchTarget;
        }
        throw new IllegalStateException("Completed implementation PATCH requires implementationPatchTarget.");
    }
}
