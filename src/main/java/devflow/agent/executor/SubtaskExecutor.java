package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.supervisor.GenerationRecoveryAction;
import devflow.agent.supervisor.GenerationRecoveryDecision;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责 implementation 阶段中“单个子任务”的执行闭环。
 * 这里只处理子任务级生成、自检、验证与恢复，不负责阶段级计划生成和最终 gate 汇总。
 */
class SubtaskExecutor {

    private final LlmProvider llmProvider;
    private final TestExecutor testExecutor;
    private final ImplementationCompletenessGate implementationCompletenessGate;
    private final ArchitectIntegrationCheck architectIntegrationCheck;
    private final SupervisorAgent supervisorAgent;
    private final SubtaskVerificationSupport subtaskVerificationSupport;
    private final SubtaskRecoverySupport subtaskRecoverySupport;
    private final SubtaskAttemptRunner subtaskAttemptRunner;
    private final int maxSubtaskAttempts;

    SubtaskExecutor(
            LlmProvider llmProvider,
            TestExecutor testExecutor,
            ImplementationCompletenessCheck implementationCompletenessCheck,
            ArchitectIntegrationCheck architectIntegrationCheck,
            SupervisorAgent supervisorAgent,
            FileProjectWorkspace workspace,
            TargetedFileContextRenderer targetedFileContextRenderer,
            ImplementationToolLoopExecutor implementationToolLoopExecutor,
            GenerationEngine generationEngine,
            AgentTurnLoop agentTurnLoop,
            int maxSubtaskAttempts
    ) {
        this.llmProvider = llmProvider;
        this.testExecutor = testExecutor;
        this.implementationCompletenessGate = new ImplementationCompletenessGate(implementationCompletenessCheck);
        this.architectIntegrationCheck = architectIntegrationCheck;
        this.supervisorAgent = supervisorAgent;
        this.maxSubtaskAttempts = maxSubtaskAttempts;
        this.subtaskVerificationSupport = new SubtaskVerificationSupport(
                testExecutor,
                llmProvider,
                generationEngine,
                this.implementationCompletenessGate,
                architectIntegrationCheck,
                workspace,
                agentTurnLoop
        );
        this.subtaskAttemptRunner = new SubtaskAttemptRunner(
                testExecutor,
                this.implementationCompletenessGate,
                targetedFileContextRenderer,
                implementationToolLoopExecutor,
                this.subtaskVerificationSupport,
                agentTurnLoop
        );
        this.subtaskRecoverySupport = new SubtaskRecoverySupport(supervisorAgent);
    }

    SubtaskExecutionReport executeSubtask(
            Path projectPath,
            RunRecord runRecord,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String inheritedFeedback,
            String persistentRepairFeedback,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            boolean finalSubtask,
            DocumentLanguage language,
            String coderContextMarkdown,
            ImplementationEventJournal eventJournal,
            SubtaskExecutionState initialExecutionState
    ) {
        List<SubtaskAttemptReport> attempts = new ArrayList<>();
        String feedback = inheritedFeedback == null ? "" : inheritedFeedback;
        SubtaskExecutionState executionState = initialExecutionState == null
                ? new SubtaskExecutionState(
                        subtask.deliveryMode(),
                        deliveryPolicy.preferPreciseEditing()
                )
                : initialExecutionState.copy();
        for (int attempt = 1; attempt <= maxSubtaskAttempts; attempt++) {
            appendImplementationEvent(
                    eventJournal,
                    ImplementationEventMessages.subtaskAttemptStart(subtask.title(), attempt, maxSubtaskAttempts)
            );
            SubtaskAttemptResult attemptOutcome = subtaskAttemptRunner.run(
                    projectPath,
                    runRecord,
                    planSummary,
                    subtask,
                    taskPackage,
                    feedback,
                    executionState,
                    contractView,
                    qualityPlan,
                    fingerprint,
                    finalSubtask,
                    language,
                    coderContextMarkdown,
                    eventJournal
            );
            if (attemptOutcome.generationFailure() != null) {
                GenerationFailureException generationFailure = attemptOutcome.generationFailure();
                executionState.applyFileScopedGenerationFailure(subtask, generationFailure);
                GenerationFailureReport failureReport = generationFailure.report();
                GenerationRecoveryDecision recoveryDecision = decideGenerationRecovery(
                        projectPath,
                        runRecord,
                        subtask,
                        feedback,
                        attempt,
                        failureReport,
                        executionState
                );
                attempts.add(SubtaskAttemptReport.fromGenerationFailure(attempt, failureReport, recoveryDecision, language));
                if (recoveryDecision.action() == GenerationRecoveryAction.FAIL_SUBTASK || attempt == maxSubtaskAttempts) {
                    return new SubtaskExecutionReport(subtask, false, attempts, executionState.copy());
                }
                executionState = executionState.withRecoveryPolicy(recoveryDecision.deliveryPolicy());
                feedback = subtaskRecoverySupport.mergeFeedback(
                        persistentRepairFeedback,
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
                        eventJournal,
                        ImplementationEventMessages.subtaskAttemptApproved(subtask.title(), attempt, maxSubtaskAttempts)
                );
                return new SubtaskExecutionReport(subtask, true, attempts, executionState.copy());
            }
            appendImplementationEvent(
                    eventJournal,
                    ImplementationEventMessages.subtaskAttemptRejected(subtask.title(), attempt, maxSubtaskAttempts, verification.decision())
            );
            if (verification.revisionRoute() == ReviewRevisionRoute.REQUEST_HUMAN) {
                return new SubtaskExecutionReport(subtask, false, attempts, executionState.copy());
            }
            executionState.applyRevisionDirective(revisionDirective);
            feedback = subtaskRecoverySupport.mergeFeedback(
                    persistentRepairFeedback,
                    subtaskVerificationSupport.buildRetryFeedback(selfCheck, verification, completenessResult)
            );
        }
        return new SubtaskExecutionReport(subtask, false, attempts, executionState.copy());
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
