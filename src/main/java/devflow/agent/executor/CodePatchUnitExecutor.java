package devflow.agent.executor;

import devflow.agent.editing.StructuredDiffPatch;

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
    private final SyntaxRepairSupport syntaxRepairSupport;
    private final PatchExecutionSupport executionSupport;
    private final PatchAttemptFailureSupport attemptFailureSupport;
    private final CodePatchFeedbackPolicyFactory feedbackPolicyFactory;
    private final int maxFileGenerationAttempts;

    CodePatchUnitExecutor(
            LlmProvider llmProvider,
            GenerationEngine generationEngine,
            GeneratedPayloadSupport generatedPayloadSupport,
            LanguageEditAdapter codeEditAdapter,
            PatchFailureRouter patchFailureRouter,
            PatchBudgetPolicy patchBudgetPolicy,
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            SyntaxRepairSupport syntaxRepairSupport,
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
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
    }

    String execute(
            CodePatchRequest request,
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
                        StructuredDiffPatch patch = patchPayloadRepairSupport.readStructuredPayload(
                                request.relativePath(),
                                unit,
                                generated,
                                StructuredDiffPatch.class,
                                request.eventJournal()
                        );
                        PatchApplyResult applyResult = codeEditAdapter.applyPatch(
                                request.projectPath(),
                                request.relativePath(),
                                currentContent,
                                patch
                        );
                        if (applyResult.succeeded()) {
                            return GenerationAttemptResult.success(applyResult.content());
                        }
                        PatchFailure patchFailure = PatchFailure.fromToolResult(
                                applyResult.failureResult(),
                                GenerationFailureType.RESULT_FILE_INVALID
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
                                GenerationFailureType.RESULT_FILE_INVALID
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
