package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.generation.GenerationAttemptResult;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureExceptions;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.generation.GenerationSpec;
import devflow.agent.executor.llm.LlmFailureReason;
import devflow.agent.executor.llm.LlmInvocationException;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.domain.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.StructureGateEvaluator;
import devflow.agent.quality.StructureGateOutcome;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.review.StructuredReviewResult;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.List;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.gate.ImplementationCompletenessGate;
import devflow.agent.executor.gate.ImplementationCompletenessGateOutcome;
import devflow.agent.executor.gate.ImplementationCompletenessResult;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.testing.TestExecutor;
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
public final class SubtaskVerificationSupport {

    private final TestExecutor testExecutor;
    private final LlmProvider llmProvider;
    private final GenerationEngine generationEngine;
    private final ImplementationCompletenessGate implementationCompletenessGate;
    private final AgentTurnLoop agentTurnLoop;
    private final SubtaskReviewPolicy subtaskReviewPolicy;
    private final SubtaskPerformanceGuidanceResolver performanceGuidanceResolver;
    private final SubtaskReviewPromptAssembler promptAssembler = new SubtaskReviewPromptAssembler();
    private final SubtaskReviewObserverFactory reviewObserverFactory = new SubtaskReviewObserverFactory();
    private final SubtaskRunnableMilestoneGuard runnableMilestoneGuard;
    private final SubtaskRuntimeWiringGuard runtimeWiringGuard;
    private final SubtaskRetryFeedbackRenderer retryFeedbackRenderer;
    private final ImplementationSelfCheckReviewResolver selfCheckReviewResolver = new ImplementationSelfCheckReviewResolver();
    private final StructureGateEvaluator structureGateEvaluator = new StructureGateEvaluator();
    private final SubtaskBoundaryGate subtaskBoundaryGate = new SubtaskBoundaryGate();
    private final SubtaskRepairDirectiveResolver repairDirectiveResolver = new SubtaskRepairDirectiveResolver();

    public SubtaskVerificationSupport(
            TestExecutor testExecutor,
            LlmProvider llmProvider,
            GenerationEngine generationEngine,
            ImplementationCompletenessGate implementationCompletenessGate,
            ArchitectIntegrationCheck architectIntegrationCheck,
            FileProjectWorkspace workspace,
            AgentTurnLoop agentTurnLoop,
            SubtaskReviewPolicy subtaskReviewPolicy,
            SubtaskPerformanceGuidanceResolver performanceGuidanceResolver,
            TreeSitterSupport treeSitterSupport
    ) {
        this.testExecutor = testExecutor;
        this.llmProvider = llmProvider;
        this.generationEngine = generationEngine;
        this.implementationCompletenessGate = implementationCompletenessGate;
        this.agentTurnLoop = agentTurnLoop;
        this.subtaskReviewPolicy = subtaskReviewPolicy;
        this.performanceGuidanceResolver = performanceGuidanceResolver;
        this.runnableMilestoneGuard = new SubtaskRunnableMilestoneGuard(architectIntegrationCheck);
        this.runtimeWiringGuard = new SubtaskRuntimeWiringGuard(workspace, treeSitterSupport);
        this.retryFeedbackRenderer = new SubtaskRetryFeedbackRenderer(promptAssembler);
    }

    SubtaskVerificationOutcome verifySubtask(
            Path projectPath,
            RunRecord runRecord,
            Subtask subtask,
            SelfCheckResult selfCheck,
            List<ToolResult> selfCheckToolResults,
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
        SubtaskVerificationOutcome runnableMilestoneReview = runnableMilestoneGuard.check(projectPath, subtask, contractView, language);
        if (runnableMilestoneReview != null) {
            return runnableMilestoneReview;
        }
        SubtaskVerificationOutcome runtimeWiringReview = runtimeWiringGuard.check(projectPath, subtask, language);
        if (runtimeWiringReview != null) {
            return runtimeWiringReview;
        }
        StructureGateOutcome structureGateOutcome = structureGateEvaluator.evaluate(fingerprint, qualityPlan);
        if (!structureGateOutcome.passed()) {
            ReviewResult structureReview = new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    structureGateOutcome.summary(),
                    structureGateOutcome.changeRequest(),
                    structureGateOutcome.evidence(),
                    "",
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
            );
            return repairDirectiveResolver.resolveCurrentScopePatch(subtask, structureReview, language);
        }
        ReviewResult selfCheckReview = selfCheckReviewResolver.resolve(selfCheck, selfCheckToolResults, language);
        if (selfCheckReview != null) {
            return repairDirectiveResolver.resolveCurrentScopePatch(subtask, selfCheckReview, language);
        }
        SubtaskVerificationOutcome functionalVerification = testExecutor.verifyImplementationSubtask(
                projectPath,
                subtask,
                contractView,
                qualityPlan,
                fingerprint,
                finalSubtask,
                language
        );
        if (functionalVerification != null) {
            return functionalVerification;
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
        StructuredReviewResult structuredReview = reviewWithObservation(subtask, candidate, language, eventJournal);
        ReviewResult review = subtaskBoundaryGate.enforce(subtask, structuredReview, language);
        ReviewResult enforcedReview = enforceCompleteness(completenessOutcome, review, language);
        return repairDirectiveResolver.resolveStructuredPatch(subtask, enforcedReview, structuredReview, language);
    }

    String buildRetryFeedback(
            SelfCheckResult selfCheck,
            ReviewResult verification,
            ImplementationCompletenessResult completenessResult
    ) {
        return retryFeedbackRenderer.render(selfCheck, verification, completenessResult);
    }

    private StructuredReviewResult reviewWithObservation(
            Subtask subtask,
            String candidate,
            DocumentLanguage language,
            ImplementationEventJournal eventJournal
    ) {
        try {
            return generationEngine.execute(new GenerationSpec<>(
                    "implementation-subtask-review:" + subtask.title(),
                    subtaskReviewPolicy.maxAttempts(),
                    subtaskReviewPolicy.heartbeatInterval(),
                    subtaskReviewPolicy.attemptTimeout(),
                    reviewObserverFactory.create(subtask.title(), eventJournal),
                    (attempt, retryFeedback) -> GenerationAttemptResult.success(
                                    llmProvider.reviewStructured(
                                            promptAssembler.systemPrompt(),
                                            retryFeedback == null || retryFeedback.isBlank()
                                                    ? candidate
                                                    : candidate + "\n\n上一轮结构化验证调用失败，请仅重新输出审阅 JSON：\n" + retryFeedback,
                                            java.util.Map.of(),
                                            ModelRole.CODE_REVIEW
                            )
                    ),
                    this::mapReviewInvocationFailure,
                    (failureType, evidence) -> GenerationFailureExceptions.create(
                            subtask.title(),
                            subtask.deliveryMode().name(),
                            "subtask-review",
                            failureType,
                            subtaskReviewPolicy.maxAttempts(),
                            false,
                            "子任务验证调用失败",
                            evidence,
                            "请重新发起当前子任务验证，并保留已有实现结果。"
                    ),
                    llmProvider::consumeLastTelemetry
            ));
        } catch (GenerationFailureException exception) {
            return new StructuredReviewResult(
                    new ReviewResult(
                            ReviewDecision.REVISION_REQUIRED,
                            FixMode.PATCH,
                            language.choose("子任务验证调用超时或失败，暂不放行当前子任务。", "Subtask verification timed out or failed, so the subtask cannot be approved yet."),
                            language.choose("请保持当前实现结果不变，重新执行当前子任务验证。", "Keep the current implementation result and rerun the current subtask verification."),
                            exception.report().evidence(),
                            language.choose("先重新验证当前子任务；若多次超时，再收缩验证输入。", "Retry the current subtask verification first; if it times out repeatedly, shrink the verification input."),
                            ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                    ),
                    null
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
