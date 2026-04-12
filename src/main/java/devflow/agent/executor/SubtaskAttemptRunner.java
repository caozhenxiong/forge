package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.loop.AgentTurnSnapshot;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

/**
 * 负责一次子任务尝试的完整 turn loop。
 * 它只覆盖 prepare/select/apply/observe/review 这一个闭环，不决定多轮重试与恢复策略。
 */
final class SubtaskAttemptRunner {

    private final TestExecutor testExecutor;
    private final ImplementationCompletenessGate implementationCompletenessGate;
    private final SubtaskVerificationSupport subtaskVerificationSupport;
    private final AgentTurnLoop agentTurnLoop;
    private final SubtaskAttemptStepExecutor stepExecutor;

    SubtaskAttemptRunner(
            TestExecutor testExecutor,
            ImplementationCompletenessGate implementationCompletenessGate,
            TargetedFileContextRenderer targetedFileContextRenderer,
            ImplementationToolLoopExecutor implementationToolLoopExecutor,
            SubtaskVerificationSupport subtaskVerificationSupport,
            AgentTurnLoop agentTurnLoop
    ) {
        this.testExecutor = testExecutor;
        this.implementationCompletenessGate = implementationCompletenessGate;
        this.subtaskVerificationSupport = subtaskVerificationSupport;
        this.agentTurnLoop = agentTurnLoop;
        this.stepExecutor = new SubtaskAttemptStepExecutor(
                testExecutor,
                implementationCompletenessGate,
                targetedFileContextRenderer,
                implementationToolLoopExecutor,
                subtaskVerificationSupport
        );
    }

    SubtaskAttemptResult run(
            Path projectPath,
            RunRecord runRecord,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String feedback,
            SubtaskExecutionState executionState,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            boolean finalSubtask,
            DocumentLanguage language,
            String coderContextMarkdown,
            ImplementationEventJournal eventJournal
    ) {
        SubtaskAttemptContext context = new SubtaskAttemptContext(
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
        SubtaskAttemptProgress progress = new SubtaskAttemptProgress();
        agentTurnLoop.runUntilSettled(
                AgentTurnSnapshot.start(),
                snapshot -> stepExecutor.handle(snapshot, context, progress)
        );
        return progress.toResult();
    }
}
