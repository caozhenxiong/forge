package devflow.agent.executor;

/**
 * 宿主内嵌 patch 的单个单元执行器。
 *
 * <p>它只负责：
 * 1. 组装单元 prompt；
 * 2. 调模型生成结构化 patch；
 * 3. apply/verify 当前单元；
 * 4. 产出结构化 retry feedback。
 *
 * <p>它不负责：
 * 1. 单元队列编排；
 * 2. 宿主 HTML 回填；
 * 3. 结构性改道。
 */
final class EmbeddedPatchUnitExecutor {

    private static final GenerationFailureClassifier GENERATION_FAILURE_CLASSIFIER = new GenerationFailureClassifier();

    private final LlmProvider llmProvider;
    private final GenerationEngine generationEngine;
    private final PatchFailureRouter patchFailureRouter;
    private final PatchBudgetPolicy patchBudgetPolicy;
    private final PatchExecutionSupport executionSupport;
    private final PatchAttemptFailureSupport attemptFailureSupport;
    private final EmbeddedPatchFeedbackPolicyFactory feedbackPolicyFactory;
    private final EmbeddedPatchPromptSupport promptSupport;
    private final EmbeddedPatchApplySupport applySupport;
    private final SyntaxRepairSupport syntaxRepairSupport;
    private final int maxFileGenerationAttempts;

    EmbeddedPatchUnitExecutor(
            LlmProvider llmProvider,
            GenerationEngine generationEngine,
            GeneratedPayloadSupport generatedPayloadSupport,
            CodePatchKernel codePatchKernel,
            PatchFailureRouter patchFailureRouter,
            PatchBudgetPolicy patchBudgetPolicy,
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            SyntaxRepairSupport syntaxRepairSupport,
            PatchContextBuilder patchContextBuilder,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            ImplementationGenerationObserverFactory implementationGenerationObserverFactory,
            int maxFileGenerationAttempts
    ) {
        this.llmProvider = llmProvider;
        this.generationEngine = generationEngine;
        this.patchFailureRouter = patchFailureRouter;
        this.patchBudgetPolicy = patchBudgetPolicy;
        this.executionSupport = new PatchExecutionSupport(
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory
        );
        this.attemptFailureSupport = new PatchAttemptFailureSupport(
                GENERATION_FAILURE_CLASSIFIER,
                patchFailureRouter
        );
        this.feedbackPolicyFactory = new EmbeddedPatchFeedbackPolicyFactory();
        this.promptSupport = new EmbeddedPatchPromptSupport(patchContextBuilder, executionSupport);
        this.applySupport = new EmbeddedPatchApplySupport(
                patchPayloadRepairSupport,
                codePatchKernel
        );
        this.syntaxRepairSupport = syntaxRepairSupport;
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
    }

    String execute(
            EmbeddedPatchRequest request,
            EmbeddedPatchKind patchKind,
            String currentContent,
            EditUnit unit
    ) {
        PatchGenerationPrompt generationPrompt = promptSupport.assemble(request, patchKind, currentContent, unit);
        String generationSystem = generationPrompt.systemPrompt();
        return generationEngine.execute(new GenerationSpec<>(
                patchKind.operationName(request.relativePath(), unit.label()),
                patchFailureRouter.attemptsFor(unit, maxFileGenerationAttempts),
                GenerationExecutionPolicy.defaultHeartbeatInterval(),
                executionSupport.generationObserver(
                        request.relativePath(),
                        patchKind.observerName(unit.label()),
                        request.deliveryMode(),
                        request.eventJournal()
                ),
                (attempt, retryFeedback) -> {
                    String prompt = promptSupport.retryPrompt(generationPrompt, patchKind, retryFeedback);
                    try {
                        PatchApplyResult applyResult = applySupport.apply(
                                request,
                                patchKind,
                                currentContent,
                                unit,
                                llmProvider.generate(
                                        generationSystem,
                                        prompt,
                                        LlmOptions.outputBudgetRatio(
                                                patchBudgetPolicy.outputBudgetRatioForUnit(unit, patchKind.defaultOutputBudgetRatio())
                                        ),
                                        ModelRole.IMPLEMENTATION
                                )
                        );
                        if (applyResult.succeeded()) {
                            return GenerationAttemptResult.success(applyResult.content());
                        }
                        PatchFailure patchFailure = PatchFailure.fromToolResult(
                                applyResult.failureResult(),
                                GenerationFailureType.TREE_SITTER_PARSE_FAILED
                        );
                        applyResult = syntaxRepairSupport.repairEmbedded(
                                request,
                                patchKind,
                                currentContent,
                                unit,
                                patchFailure,
                                applyResult
                        );
                        if (applyResult.succeeded()) {
                            return GenerationAttemptResult.success(applyResult.content());
                        }
                        patchFailure = PatchFailure.fromToolResult(
                                applyResult.failureResult(),
                                GenerationFailureType.TREE_SITTER_PARSE_FAILED
                        );
                        if (patchFailureRouter.shouldAbortCurrentUnit(unit, patchFailure)) {
                            return GenerationAttemptResult.terminalFailure(
                                    patchFailure,
                                    EmbeddedPatchFeedbackRenderer.validationRetryFeedback(
                                            attempt,
                                            request.relativePath(),
                                            unit,
                                            patchKind,
                                            patchFailure
                                    )
                            );
                        }
                        return GenerationAttemptResult.failure(
                                patchFailure,
                                EmbeddedPatchFeedbackRenderer.validationRetryFeedback(
                                        attempt,
                                        request.relativePath(),
                                        unit,
                                        patchKind,
                                        patchFailure
                                )
                        );
                    } catch (Exception exception) {
                        return attemptFailureSupport.handle(
                                exception,
                                attempt,
                                unit,
                                feedbackPolicyFactory.create(request.relativePath(), unit, patchKind)
                        );
                    }
                },
                GENERATION_FAILURE_CLASSIFIER::classify,
                (failureType, evidence) -> executionSupport.generationFailure(
                        request.relativePath(),
                        request.deliveryMode(),
                        patchKind.observerName(unit.label()),
                        patchFailureRouter.attemptsFor(unit, maxFileGenerationAttempts),
                        failureType,
                        evidence,
                        EmbeddedPatchFeedbackRenderer.generationFailureAdvice(patchKind)
                ),
                llmProvider::consumeLastTelemetry
        ));
    }
}
