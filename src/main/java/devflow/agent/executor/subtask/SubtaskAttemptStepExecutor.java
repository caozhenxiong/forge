package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureException;

import devflow.agent.validation.ValidationExecutionReport;
import devflow.agent.loop.AgentTurnSnapshot;
import devflow.agent.loop.AgentTurnState;
import devflow.agent.loop.AgentTurnStepResult;
import devflow.agent.review.ReviewDecision;
import java.util.List;

import devflow.agent.executor.implementation.toolloop.ImplementationToolLoopExecutor;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.gate.ImplementationCompletenessGate;
import devflow.agent.executor.gate.ImplementationCompletenessGateInput;
import devflow.agent.executor.gate.ImplementationCompletenessGateOutcome;
import devflow.agent.executor.TargetedFileContextRenderer;
import devflow.agent.executor.testing.TestExecutor;
/**
 * 负责一次子任务尝试里的 turn step 状态迁移。
 *
 * <p>这里集中维护 prepare/select/apply/observe/review 这条确定性状态机，
 * `SubtaskAttemptRunner` 只保留 turn loop 门面和依赖装配。
 */
public final class SubtaskAttemptStepExecutor {

    private final TestExecutor testExecutor;
    private final ImplementationCompletenessGate implementationCompletenessGate;
    private final TargetedFileContextRenderer targetedFileContextRenderer;
    private final ImplementationToolLoopExecutor implementationToolLoopExecutor;
    private final SubtaskVerificationSupport subtaskVerificationSupport;

    public SubtaskAttemptStepExecutor(
            TestExecutor testExecutor,
            ImplementationCompletenessGate implementationCompletenessGate,
            TargetedFileContextRenderer targetedFileContextRenderer,
            ImplementationToolLoopExecutor implementationToolLoopExecutor,
            SubtaskVerificationSupport subtaskVerificationSupport
    ) {
        this.testExecutor = testExecutor;
        this.implementationCompletenessGate = implementationCompletenessGate;
        this.targetedFileContextRenderer = targetedFileContextRenderer;
        this.implementationToolLoopExecutor = implementationToolLoopExecutor;
        this.subtaskVerificationSupport = subtaskVerificationSupport;
    }

    AgentTurnStepResult handle(AgentTurnSnapshot snapshot, SubtaskAttemptContext context, SubtaskAttemptProgress progress) {
        AgentTurnState state = snapshot.state();
        if (state == AgentTurnState.IDLE) {
            return advance(snapshot, context.subtask().title(), AgentTurnState.PREPARE_CONTEXT, "prepare-subtask-context");
        }
        if (state == AgentTurnState.PREPARE_CONTEXT) {
            return advance(snapshot, context.subtask().title(), AgentTurnState.SELECT_NEXT_UNIT, "select-subtask-unit");
        }
        if (state == AgentTurnState.SELECT_NEXT_UNIT) {
            return advance(snapshot, context.subtask().title(), AgentTurnState.EXECUTE_STEP, "execute-subtask-files");
        }
        if (state == AgentTurnState.EXECUTE_STEP) {
            return executeStep(snapshot, context, progress);
        }
        if (state == AgentTurnState.OBSERVE_RESULT) {
            observeResult(context, progress);
            return advance(snapshot, context.subtask().title(), AgentTurnState.EVALUATE_RESULT, "verify-subtask-result");
        }
        if (state == AgentTurnState.EVALUATE_RESULT) {
            return evaluateResult(snapshot, context, progress);
        }
        return AgentTurnStepResult.stop(
                snapshot.next(AgentTurnState.FAILED, context.subtask().title(), "unexpected-coder-state")
        );
    }

    private AgentTurnStepResult executeStep(
            AgentTurnSnapshot snapshot,
            SubtaskAttemptContext context,
            SubtaskAttemptProgress progress
    ) {
        try {
            applySubtask(context);
            return advance(snapshot, context.subtask().title(), AgentTurnState.OBSERVE_RESULT, "subtask-files-applied");
        } catch (GenerationFailureException exception) {
            progress.generationFailure(exception);
            return AgentTurnStepResult.stop(
                    snapshot.next(AgentTurnState.REQUEST_HANDOFF, context.subtask().title(), "subtask-generation-failed")
            );
        }
    }

    private void observeResult(SubtaskAttemptContext context, SubtaskAttemptProgress progress) {
        Subtask effectiveSubtask = effectiveSubtask(context);
        ValidationExecutionReport selfCheckReport = testExecutor.selfCheckDetailed(context.projectPath());
        progress.selfCheck(selfCheckReport.selfCheckResult());
        progress.selfCheckToolResults(selfCheckReport.toolResults());
        progress.completenessOutcome(implementationCompletenessGate.evaluate(
                new ImplementationCompletenessGateInput(
                        context.projectPath(),
                        effectiveSubtask,
                        context.qualityPlan(),
                        context.finalSubtask()
                )
        ));
    }

    private AgentTurnStepResult evaluateResult(
            AgentTurnSnapshot snapshot,
            SubtaskAttemptContext context,
            SubtaskAttemptProgress progress
    ) {
        ImplementationCompletenessGateOutcome completenessOutcome = progress.completenessOutcome();
        Subtask effectiveSubtask = effectiveSubtask(context);
        SubtaskVerificationOutcome verificationOutcome = subtaskVerificationSupport.verifySubtask(
                context.projectPath(),
                context.runRecord(),
                effectiveSubtask,
                progress.selfCheck(),
                progress.selfCheckToolResults(),
                context.feedback(),
                completenessOutcome.inspection(),
                completenessOutcome,
                context.finalSubtask(),
                context.contractView(),
                context.qualityPlan(),
                context.fingerprint(),
                context.language(),
                renderTargetedContext(context, effectiveSubtask),
                context.eventJournal()
        );
        progress.verification(verificationOutcome.review());
        progress.revisionDirective(verificationOutcome.revisionDirective());
        boolean approved = progress.selfCheck() != null
                && progress.selfCheck().passed()
                && progress.verification() != null
                && progress.verification().decision() == ReviewDecision.APPROVED;
        return AgentTurnStepResult.stop(
                snapshot.next(
                        approved ? AgentTurnState.COMPLETE : AgentTurnState.REQUEST_CONTINUATION,
                        context.subtask().title(),
                        approved ? "subtask-attempt-approved" : "subtask-attempt-needs-retry"
                )
        );
    }

    private void applySubtask(SubtaskAttemptContext context) {
        Subtask effectiveSubtask = effectiveSubtask(context);
        if (effectiveSubtask.changes() == null || effectiveSubtask.changes().isEmpty()) {
            return;
        }
        TaskPackage effectiveTaskPackage = context.taskPackage() == null
                ? null
                : context.taskPackage().alignToSubtask(effectiveSubtask);
        implementationToolLoopExecutor.execute(
                context.projectPath(),
                context.runRecord(),
                effectiveSubtask,
                effectiveTaskPackage,
                context.contractView(),
                context.qualityPlan(),
                context.fingerprint(),
                context.feedback(),
                context.coderContextMarkdown(),
                context.eventJournal(),
                context.executionState()
        );
    }

    private AgentTurnStepResult advance(
            AgentTurnSnapshot snapshot,
            String title,
            AgentTurnState nextState,
            String reason
    ) {
        return AgentTurnStepResult.advance(snapshot.next(nextState, title, reason));
    }

    private Subtask effectiveSubtask(SubtaskAttemptContext context) {
        List<FileChange> activeChanges = context.executionState() == null
                ? (context.subtask().changes() == null ? List.of() : context.subtask().changes())
                : context.executionState().effectiveChanges(context.subtask().changes());
        return new Subtask(
                context.subtask().title(),
                context.subtask().goal(),
                context.subtask().coverageRefs(),
                context.subtask().ownedCapabilities(),
                context.subtask().deferredCapabilities(),
                context.subtask().acceptanceCriteria(),
                context.subtask().runnableMilestone(),
                context.executionState() == null ? context.subtask().deliveryMode() : context.executionState().deliveryMode(),
                activeChanges
        );
    }

    private String renderTargetedContext(SubtaskAttemptContext context, Subtask effectiveSubtask) {
        if (targetedFileContextRenderer == null) {
            return "";
        }
        return targetedFileContextRenderer.render(
                context.projectPath(),
                effectiveSubtask.changes(),
                null,
                context.contractView(),
                context.fingerprint()
        );
    }
}
