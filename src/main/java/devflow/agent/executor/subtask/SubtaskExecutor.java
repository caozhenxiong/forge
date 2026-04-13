package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureReport;

import devflow.agent.domain.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.supervisor.GenerationRecoveryAction;
import devflow.agent.supervisor.GenerationRecoveryDecision;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.implementation.ImplementationEventMessages;
import devflow.agent.executor.gate.ImplementationCompletenessGateOutcome;
import devflow.agent.executor.gate.ImplementationCompletenessResult;
import devflow.agent.executor.SelfCheckResult;
/**
 * 负责 implementation 阶段中“单个子任务”的执行闭环。
 * 这里只处理子任务级生成、自检、验证与恢复，不负责阶段级计划生成和最终 gate 汇总。
 */
public class SubtaskExecutor {

    private final SubtaskVerificationSupport subtaskVerificationSupport;
    private final SubtaskRecoverySupport subtaskRecoverySupport;
    private final SubtaskAttemptRunner subtaskAttemptRunner;
    private final int maxSubtaskAttempts;

    public SubtaskExecutor(
            SubtaskVerificationSupport subtaskVerificationSupport,
            SubtaskRecoverySupport subtaskRecoverySupport,
            SubtaskAttemptRunner subtaskAttemptRunner,
            int maxSubtaskAttempts
    ) {
        this.subtaskVerificationSupport = subtaskVerificationSupport;
        this.subtaskRecoverySupport = subtaskRecoverySupport;
        this.subtaskAttemptRunner = subtaskAttemptRunner;
        this.maxSubtaskAttempts = maxSubtaskAttempts;
    }

    public SubtaskExecutionReport executeSubtask(SubtaskExecutionContext executionContext) {
        List<SubtaskAttemptReport> attempts = new ArrayList<>();
        String feedback = executionContext.inheritedFeedbackOrEmpty();
        SubtaskExecutionState executionState = executionContext.initialExecutionStateOrDefault();
        for (int attempt = 1; attempt <= maxSubtaskAttempts; attempt++) {
            appendImplementationEvent(
                    executionContext.eventJournal(),
                    ImplementationEventMessages.subtaskAttemptStart(executionContext.subtask().title(), attempt, maxSubtaskAttempts)
            );
            SubtaskAttemptResult attemptOutcome = subtaskAttemptRunner.run(
                    executionContext.toAttemptContext(feedback, executionState)
            );
            if (attemptOutcome.generationFailure() != null) {
                GenerationFailureException generationFailure = attemptOutcome.generationFailure();
                executionState.applyFileScopedGenerationFailure(executionContext.subtask(), generationFailure);
                GenerationFailureReport failureReport = generationFailure.report();
                GenerationRecoveryDecision recoveryDecision = decideGenerationRecovery(
                        executionContext.projectPath(),
                        executionContext.runRecord(),
                        executionContext.subtask(),
                        feedback,
                        attempt,
                        failureReport,
                        executionState
                );
                attempts.add(SubtaskAttemptReport.fromGenerationFailure(attempt, failureReport, recoveryDecision, executionContext.language()));
                if (recoveryDecision.action() == GenerationRecoveryAction.FAIL_SUBTASK || attempt == maxSubtaskAttempts) {
                    return new SubtaskExecutionReport(executionContext.subtask(), false, attempts, executionState.copy());
                }
                executionState = executionState.withRecoveryPolicy(recoveryDecision.deliveryPolicy());
                executionState.resetToolLoopTranscript();
                feedback = subtaskRecoverySupport.mergeFeedback(
                        executionContext.persistentRepairFeedback(),
                        subtaskRecoverySupport.buildGenerationRetryFeedback(failureReport, recoveryDecision)
                );
                continue;
            }
            SelfCheckResult selfCheck = attemptOutcome.selfCheck();
            List<ToolResult> selfCheckToolResults = attemptOutcome.selfCheckToolResults();
            ImplementationCompletenessGateOutcome completenessOutcome = attemptOutcome.completenessOutcome();
            ImplementationCompletenessResult completenessResult = completenessOutcome.inspection();
            ReviewResult verification = attemptOutcome.verification();
            SubtaskRevisionDirective revisionDirective = attemptOutcome.revisionDirective();
            attempts.add(SubtaskAttemptReport.fromVerification(attempt, selfCheck, selfCheckToolResults, verification));
            if (selfCheck.passed() && verification.decision() == ReviewDecision.APPROVED) {
                appendImplementationEvent(
                        executionContext.eventJournal(),
                        ImplementationEventMessages.subtaskAttemptApproved(executionContext.subtask().title(), attempt, maxSubtaskAttempts)
                );
                return new SubtaskExecutionReport(executionContext.subtask(), true, attempts, executionState.copy());
            }
            appendImplementationEvent(
                    executionContext.eventJournal(),
                    ImplementationEventMessages.subtaskAttemptRejected(executionContext.subtask().title(), attempt, maxSubtaskAttempts, verification.decision())
            );
            if (verification.revisionRoute() == ReviewRevisionRoute.REQUEST_HUMAN) {
                return new SubtaskExecutionReport(executionContext.subtask(), false, attempts, executionState.copy());
            }
            executionState = executionState.applyRevisionDirective(revisionDirective);
            executionState.resetToolLoopTranscript();
            feedback = subtaskRecoverySupport.mergeFeedback(
                    executionContext.persistentRepairFeedback(),
                    subtaskVerificationSupport.buildRetryFeedback(selfCheck, verification, completenessResult)
            );
        }
        return new SubtaskExecutionReport(executionContext.subtask(), false, attempts, executionState.copy());
    }

    private GenerationRecoveryDecision decideGenerationRecovery(
            Path projectPath,
            RunRecord runRecord,
            Subtask subtask,
            String feedback,
            int subtaskAttempt,
            GenerationFailureReport failureReport,
            SubtaskExecutionState executionState
    ) {
        return subtaskRecoverySupport.decideGenerationRecovery(
                projectPath,
                runRecord,
                subtask,
                feedback,
                subtaskAttempt,
                failureReport,
                executionState
        );
    }

    private void appendImplementationEvent(ImplementationEventJournal eventJournal, String message) {
        if (eventJournal == null) {
            return;
        }
        eventJournal.append(message);
    }
}
