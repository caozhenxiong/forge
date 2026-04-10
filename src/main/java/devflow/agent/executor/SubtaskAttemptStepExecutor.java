package devflow.agent.executor;

import devflow.agent.loop.AgentTurnSnapshot;
import devflow.agent.loop.AgentTurnState;
import devflow.agent.loop.AgentTurnStepResult;
import devflow.agent.review.ReviewDecision;

/**
 * 负责一次子任务尝试里的 turn step 状态迁移。
 *
 * <p>这里集中维护 prepare/select/apply/observe/review 这条确定性状态机，
 * `SubtaskAttemptRunner` 只保留 turn loop 门面和依赖装配。
 */
final class SubtaskAttemptStepExecutor {

    private final TestExecutor testExecutor;
    private final ImplementationCompletenessGate implementationCompletenessGate;
    private final FileEditCoordinator fileEditCoordinator;
    private final SubtaskVerificationSupport subtaskVerificationSupport;

    SubtaskAttemptStepExecutor(
            TestExecutor testExecutor,
            ImplementationCompletenessGate implementationCompletenessGate,
            FileEditCoordinator fileEditCoordinator,
            SubtaskVerificationSupport subtaskVerificationSupport
    ) {
        this.testExecutor = testExecutor;
        this.implementationCompletenessGate = implementationCompletenessGate;
        this.fileEditCoordinator = fileEditCoordinator;
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
        progress.selfCheck(testExecutor.selfCheck(context.projectPath()));
        progress.completenessOutcome(implementationCompletenessGate.evaluate(
                new ImplementationCompletenessGateInput(
                        context.projectPath(),
                        context.subtask(),
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
        progress.verification(subtaskVerificationSupport.verifySubtask(
                context.projectPath(),
                context.runRecord(),
                context.subtask(),
                progress.selfCheck(),
                context.feedback(),
                completenessOutcome.inspection(),
                completenessOutcome,
                context.finalSubtask(),
                context.contractView(),
                context.qualityPlan(),
                context.fingerprint(),
                context.language(),
                fileEditCoordinator.renderTargetedContext(
                        context.projectPath(),
                        context.subtask().changes(),
                        null,
                        context.contractView(),
                        context.fingerprint()
                ),
                context.eventJournal()
        ));
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
        SubtaskExecutionState executionState = context.executionState();
        for (FileChange change : context.subtask().changes()) {
            executionState = fileEditCoordinator.applyChange(
                    context.projectPath(),
                    context.planSummary(),
                    context.subtask(),
                    context.taskPackage(),
                    context.feedback(),
                    change,
                    context.executionState(),
                    context.contractView(),
                    context.fingerprint(),
                    context.coderContextMarkdown(),
                    context.eventJournal()
            );
        }
    }

    private AgentTurnStepResult advance(
            AgentTurnSnapshot snapshot,
            String title,
            AgentTurnState nextState,
            String reason
    ) {
        return AgentTurnStepResult.advance(snapshot.next(nextState, title, reason));
    }
}
