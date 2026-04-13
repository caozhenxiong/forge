package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.generation.GenerationAttemptResult;
import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.generation.GenerationExecutionPolicy;
import devflow.agent.executor.generation.GenerationFailureClassifier;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.generation.GenerationSpec;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

import devflow.agent.editing.ExactReplaceEdit;

/**
 * 负责 `precise-code` 的单个 patch 单元执行。
 * 它只处理 prompt -> generate -> normalize -> apply/verify 这一条链，不管理单元队列。
 */
final class CodePatchUnitExecutor {

    private static final GenerationFailureClassifier GENERATION_FAILURE_CLASSIFIER = new GenerationFailureClassifier();

    private final LlmProvider llmProvider;
    private final GenerationEngine generationEngine;
    private final GeneratedPayloadSupport generatedPayloadSupport;
    private final LanguageEditAdapter codeEditAdapter;
    private final PatchFailureRouter patchFailureRouter;
    private final PatchBudgetPolicy patchBudgetPolicy;
    private final PatchPayloadRepairSupport patchPayloadRepairSupport;
    private final ExactReplaceSemanticRepairSupport exactReplaceSemanticRepairSupport;
    private final SyntaxRepairSupport syntaxRepairSupport;
    private final PatchExecutionSupport executionSupport;
    private final PatchAttemptFailureSupport attemptFailureSupport;
    private final CodePatchFeedbackPolicyFactory feedbackPolicyFactory;
    private final RepairScopeValidator repairScopeValidator;
    private final int maxFileGenerationAttempts;

    CodePatchUnitExecutor(
            LlmProvider llmProvider,
            GenerationEngine generationEngine,
            GeneratedPayloadSupport generatedPayloadSupport,
            LanguageEditAdapter codeEditAdapter,
            PatchFailureRouter patchFailureRouter,
            PatchBudgetPolicy patchBudgetPolicy,
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            ExactReplaceSemanticRepairSupport exactReplaceSemanticRepairSupport,
            SyntaxRepairSupport syntaxRepairSupport,
            PatchContextBuilder patchContextBuilder,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            ImplementationGenerationObserverFactory implementationGenerationObserverFactory,
            int maxFileGenerationAttempts
    ) {
        this.llmProvider = llmProvider;
        this.generationEngine = generationEngine;
        this.generatedPayloadSupport = generatedPayloadSupport;
        this.codeEditAdapter = codeEditAdapter;
        this.patchFailureRouter = patchFailureRouter;
        this.patchBudgetPolicy = patchBudgetPolicy;
        this.patchPayloadRepairSupport = patchPayloadRepairSupport;
        this.exactReplaceSemanticRepairSupport = exactReplaceSemanticRepairSupport;
        this.syntaxRepairSupport = syntaxRepairSupport;
        this.executionSupport = new PatchExecutionSupport(
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory
        );
        this.attemptFailureSupport = new PatchAttemptFailureSupport(
                GENERATION_FAILURE_CLASSIFIER,
                patchFailureRouter
        );
        this.feedbackPolicyFactory = new CodePatchFeedbackPolicyFactory();
        this.repairScopeValidator = new RepairScopeValidator(patchContextBuilder);
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
    }

    String execute(
            CodeTargetedRewriteRequest request,
            String currentContent,
            EditUnit unit,
            PatchContextBuilder patchContextBuilder
    ) {
        boolean restrictedUnit = isRestrictedUnit(unit);
        boolean strictBodyOnlyUnit = isStrictBodyOnlyCodeUnit(unit);
        boolean scaffoldBootstrapUnit = isScaffoldBootstrapUnit(unit, currentContent);
        PatchGenerationPrompt generationPrompt = CodePatchPromptAssembler.assemble(
                request.relativePath(),
                request.planSummary(),
                request.taskPackageMarkdown(),
                executionSupport.nullToEmpty(request.coderContextMarkdown()),
                request.reason(),
                executionSupport.nullToEmpty(request.feedback()),
                patchContextBuilder.describeCodeTargets(request.relativePath(), currentContent),
                request.targetedContext(),
                executionSupport.summarizeForVerification(
                        currentContent.isBlank() ? devflow.agent.i18n.PlaceholderValues.machineNewFile() : currentContent,
                        GenerationBudgetProfile.fileContextPreviewChars()
                ),
                currentContent,
                unit,
                restrictedUnit,
                strictBodyOnlyUnit
        );
        String generationSystem = generationPrompt.systemPrompt();
        String generationUser = generationPrompt.userPrompt();
        return generationEngine.execute(new GenerationSpec<>(
                FileEditStrategyNames.operation(FileEditStrategyNames.PRECISE_CODE, request.relativePath(), unit.label()),
                patchFailureRouter.attemptsFor(unit, maxFileGenerationAttempts),
                GenerationExecutionPolicy.defaultHeartbeatInterval(),
                executionSupport.generationObserver(
                        request.relativePath(),
                        FileEditStrategyNames.observer(FileEditStrategyNames.PRECISE_CODE, unit.label()),
                        request.deliveryMode(),
                        request.eventJournal()
                ),
                (attempt, retryFeedback) -> {
                    String prompt = RetryPromptComposer.append(
                            generationUser,
                            retryFeedback,
                            "上一轮符号级改写失败，请修正后重新生成："
                    );
                    try {
                        String generated = generatedPayloadSupport.normalizeGeneratedPayload(
                                request.relativePath(),
                                        llmProvider.generate(
                                                generationSystem,
                                                prompt,
                                                LlmOptions.outputBudgetRatio(
                                                        patchBudgetPolicy.outputBudgetRatioForUnit(
                                                                unit,
                                                                scaffoldBootstrapUnit
                                                                        ? GenerationBudgetProfile.preciseCodeScaffoldOutputRatio()
                                                                        : GenerationBudgetProfile.preciseCodeUnitOutputRatio()
                                                        )
                                                ),
                                                ModelRole.IMPLEMENTATION
                                        )
                        );
                        ExactReplaceEdit edit = patchPayloadRepairSupport.readStructuredPayload(
                                request.relativePath(),
                                unit,
                                generated,
                                ExactReplaceEdit.class,
                                request.eventJournal()
                        );
                        PatchApplyResult applyResult = codeEditAdapter.applyPatch(
                                request.projectPath(),
                                request.relativePath(),
                                currentContent,
                                edit
                        );
                        applyResult = repairSemanticPatchFailure(
                                request,
                                currentContent,
                                unit,
                                generated,
                                edit,
                                applyResult
                        );
                        applyResult = validateRestrictedScope(request, currentContent, unit, applyResult);
                        if (applyResult.succeeded()) {
                            return GenerationAttemptResult.success(applyResult.content());
                        }
                        PatchFailure patchFailure = PatchFailure.fromToolResult(
                                applyResult.failureResult(),
                                GenerationFailureType.VALIDATION_FAILED
                        );
                        applyResult = syntaxRepairSupport.repairCodeFile(
                                request,
                                currentContent,
                                unit,
                                patchFailure,
                                applyResult
                        );
                        if (applyResult.succeeded()) {
                            return GenerationAttemptResult.success(applyResult.content());
                        }
                        ToolResult failureResult = applyResult.failureResult();
                        patchFailure = PatchFailure.fromToolResult(
                                failureResult,
                                GenerationFailureType.VALIDATION_FAILED
                        );
                        if (patchFailureRouter.shouldAbortCurrentUnit(unit, patchFailure)) {
                            return GenerationAttemptResult.terminalFailure(
                                    patchFailure,
                                    CodePatchFeedbackRenderer.validationRetryFeedback(
                                            attempt,
                                            request.relativePath(),
                                            unit,
                                            patchFailure
                                    )
                            );
                        }
                        return GenerationAttemptResult.failure(
                                patchFailure,
                                CodePatchFeedbackRenderer.validationRetryFeedback(
                                        attempt,
                                        request.relativePath(),
                                        unit,
                                        patchFailure
                                )
                        );
                    } catch (Exception exception) {
                        return attemptFailureSupport.handle(
                                exception,
                                attempt,
                                unit,
                                feedbackPolicyFactory.create(request.relativePath(), unit, attempt)
                        );
                    }
                },
                GENERATION_FAILURE_CLASSIFIER::classify,
                (failureType, evidence) -> executionSupport.generationFailure(
                        request.relativePath(),
                        request.deliveryMode(),
                        FileEditStrategyNames.observer(FileEditStrategyNames.PRECISE_CODE, unit.label()),
                        patchFailureRouter.attemptsFor(unit, maxFileGenerationAttempts),
                        failureType,
                        evidence,
                        CodePatchFeedbackRenderer.generationFailureAdvice(failureType)
                ),
                llmProvider::consumeLastTelemetry
        ));
    }

    private PatchApplyResult repairSemanticPatchFailure(
            CodeTargetedRewriteRequest request,
            String currentContent,
            EditUnit unit,
            String normalizedPayload,
            ExactReplaceEdit edit,
            PatchApplyResult applyResult
    ) {
        if (applyResult == null || applyResult.succeeded() || applyResult.failureResult() == null) {
            return applyResult;
        }
        PatchFailure failure = PatchFailure.fromToolResult(
                applyResult.failureResult(),
                GenerationFailureType.VALIDATION_FAILED
        );
        ExactReplaceEdit repairedEdit = exactReplaceSemanticRepairSupport.repair(
                request.relativePath(),
                unit,
                currentContent,
                normalizedPayload,
                edit,
                failure,
                request.eventJournal()
        );
        if (repairedEdit == null) {
            return applyResult;
        }
        return codeEditAdapter.applyPatch(
                request.projectPath(),
                request.relativePath(),
                currentContent,
                repairedEdit
        );
    }

    /**
     * exact-replace 主链也必须保留 restricted unit 的本地 scope guard。
     * 这里在首次 apply 成功后立刻校验，避免模型通过整段替换偷偷扩到 allowedSymbols 之外。
     */
    private PatchApplyResult validateRestrictedScope(
            CodeTargetedRewriteRequest request,
            String baselineContent,
            EditUnit unit,
            PatchApplyResult applyResult
    ) {
        if (applyResult == null || !applyResult.succeeded()) {
            return applyResult;
        }
        ToolResult scopeResult = repairScopeValidator.verifyCodeFile(
                request.relativePath(),
                baselineContent,
                applyResult.content(),
                unit
        );
        if (scopeResult.succeeded()) {
            return applyResult;
        }
        return new PatchApplyResult(applyResult.content(), applyResult.applyResult(), scopeResult);
    }

    private boolean isRestrictedUnit(EditUnit unit) {
        return unit != null && unit.restrictsSymbols();
    }

    private boolean isStrictBodyOnlyCodeUnit(EditUnit unit) {
        return isRestrictedUnit(unit) && !unit.splittable();
    }

    private boolean isScaffoldBootstrapUnit(EditUnit unit, String currentContent) {
        return unit != null
                && !unit.restrictsSymbols()
                && (currentContent == null || currentContent.isBlank());
    }
}
