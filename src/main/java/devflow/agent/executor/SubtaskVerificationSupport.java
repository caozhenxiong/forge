package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.StructureGateEvaluator;
import devflow.agent.quality.StructureGateOutcome;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.List;

/**
 * 子任务验证闭环支撑。
 *
 * <p>它负责：
 * 1. runnable milestone 的确定性检查；
 * 2. 子任务 review 的可观测调用；
 * 3. 完整性 gate 与 review 结果的合并；
 * 4. retry feedback 的结构化拼装。
 *
 * <p>这样 `SubtaskExecutor` 只保留尝试循环和文件应用编排，不再继续混着 review 语义与验证文案。
 */
final class SubtaskVerificationSupport {

    private final LlmProvider llmProvider;
    private final GenerationEngine generationEngine;
    private final ImplementationCompletenessGate implementationCompletenessGate;
    private final AgentTurnLoop agentTurnLoop;
    private final SubtaskReviewPromptAssembler promptAssembler = new SubtaskReviewPromptAssembler();
    private final SubtaskReviewObserverFactory reviewObserverFactory = new SubtaskReviewObserverFactory();
    private final SubtaskPerformanceGuidanceResolver performanceGuidanceResolver = new SubtaskPerformanceGuidanceResolver();
    private final SubtaskRunnableMilestoneGuard runnableMilestoneGuard;
    private final SubtaskRuntimeWiringGuard runtimeWiringGuard;
    private final SubtaskRetryFeedbackRenderer retryFeedbackRenderer;
    private final StructureGateEvaluator structureGateEvaluator = new StructureGateEvaluator();

    SubtaskVerificationSupport(
            LlmProvider llmProvider,
            GenerationEngine generationEngine,
            ImplementationCompletenessGate implementationCompletenessGate,
            ArchitectIntegrationCheck architectIntegrationCheck,
            FileProjectWorkspace workspace,
            AgentTurnLoop agentTurnLoop
    ) {
        this.llmProvider = llmProvider;
        this.generationEngine = generationEngine;
        this.implementationCompletenessGate = implementationCompletenessGate;
        this.agentTurnLoop = agentTurnLoop;
        this.runnableMilestoneGuard = new SubtaskRunnableMilestoneGuard(architectIntegrationCheck);
        this.runtimeWiringGuard = new SubtaskRuntimeWiringGuard(workspace);
        this.retryFeedbackRenderer = new SubtaskRetryFeedbackRenderer(promptAssembler);
    }

    ReviewResult verifySubtask(
            Path projectPath,
            RunRecord runRecord,
            Subtask subtask,
            SelfCheckResult selfCheck,
            String feedback,
            ImplementationCompletenessResult completenessResult,
            ImplementationCompletenessGateOutcome completenessOutcome,
            boolean finalSubtask,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            DocumentLanguage language,
            String targetedContext,
            ImplementationEventJournal eventJournal
    ) {
        ReviewResult runnableMilestoneReview = runnableMilestoneGuard.check(projectPath, subtask, contractView, language);
        if (runnableMilestoneReview != null) {
            return runnableMilestoneReview;
        }
        ReviewResult runtimeWiringReview = runtimeWiringGuard.check(projectPath, subtask, language);
        if (runtimeWiringReview != null) {
            return runtimeWiringReview;
        }
        StructureGateOutcome structureGateOutcome = structureGateEvaluator.evaluate(fingerprint, qualityPlan);
        if (!structureGateOutcome.passed()) {
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    structureGateOutcome.summary(),
                    structureGateOutcome.changeRequest(),
                    structureGateOutcome.evidence(),
                    ""
            );
        }
        String candidate = promptAssembler.candidatePrompt(
                subtask,
                selfCheck,
                completenessResult,
                qualityPlan,
                targetedContext,
                performanceGuidanceResolver.resolve(runRecord),
                promptAssembler.renderRepairVerificationContext(feedback)
        );
        ReviewResult review = reviewWithObservation(subtask, candidate, language, eventJournal);
        return enforceCompleteness(completenessOutcome, review, language);
    }

    String buildRetryFeedback(
            SelfCheckResult selfCheck,
            ReviewResult verification,
            ImplementationCompletenessResult completenessResult
    ) {
        return retryFeedbackRenderer.render(selfCheck, verification, completenessResult);
    }

    private ReviewResult reviewWithObservation(
            Subtask subtask,
            String candidate,
            DocumentLanguage language,
            ImplementationEventJournal eventJournal
    ) {
        try {
            return generationEngine.execute(new GenerationSpec<>(
                    "implementation-subtask-review:" + subtask.title(),
                    SubtaskReviewPolicy.maxAttempts(),
                    SubtaskReviewPolicy.heartbeatInterval(),
                    SubtaskReviewPolicy.attemptTimeout(),
                    reviewObserverFactory.create(subtask.title(), eventJournal),
                    (attempt, retryFeedback) -> GenerationAttemptResult.success(
                            llmProvider.review(
                                    promptAssembler.systemPrompt(),
                                    retryFeedback == null || retryFeedback.isBlank()
                                            ? candidate
                                            : candidate + "\n\n上一轮结构化验证调用失败，请仅重新输出审阅 JSON：\n" + retryFeedback,
                                    LlmOptions.outputBudgetRatio(GenerationBudgetProfile.subtaskReviewOutputRatio()),
                                    ModelRole.CODE_REVIEW
                            )
                    ),
                    this::mapReviewInvocationFailure,
                    (failureType, evidence) -> GenerationFailureExceptions.create(
                            subtask.title(),
                            subtask.deliveryMode().name(),
                            "subtask-review",
                            failureType,
                            SubtaskReviewPolicy.maxAttempts(),
                            false,
                            "子任务验证调用失败",
                            evidence,
                            "请重新发起当前子任务验证，并保留已有实现结果。"
                    ),
                    llmProvider::consumeLastTelemetry
            ));
        } catch (GenerationFailureException exception) {
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    language.choose("子任务验证调用超时或失败，暂不放行当前子任务。", "Subtask verification timed out or failed, so the subtask cannot be approved yet."),
                    language.choose("请保持当前实现结果不变，重新执行当前子任务验证。", "Keep the current implementation result and rerun the current subtask verification."),
                    exception.report().evidence(),
                    language.choose("先重新验证当前子任务；若多次超时，再收缩验证输入。", "Retry the current subtask verification first; if it times out repeatedly, shrink the verification input.")
            );
        }
    }

    private GenerationFailureType mapReviewInvocationFailure(Exception exception) {
        if (exception instanceof LlmInvocationException llmInvocationException
                && llmInvocationException.reason() == LlmFailureReason.TIMEOUT) {
            return GenerationFailureType.ATTEMPT_TIMEOUT;
        }
        return GenerationFailureType.MODEL_INVOCATION_FAILED;
    }

    private ReviewResult enforceCompleteness(
            ImplementationCompletenessGateOutcome completenessOutcome,
            ReviewResult review,
            DocumentLanguage language
    ) {
        if (completenessOutcome == null || completenessOutcome.report() == null || completenessOutcome.report().passed()) {
            return review;
        }
        return implementationCompletenessGate.toBlockingReviewResult(completenessOutcome, language);
    }

}
