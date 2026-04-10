package devflow.agent.executor;

import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.editing.HtmlPrecisePatch;

/**
 * 精确 HTML patch 执行器。
 *
 * <p>只负责 `precise-html` 路径的生成、apply 与本地 gate。
 */
final class PreciseHtmlPatchExecutor {

    private static final GenerationFailureClassifier GENERATION_FAILURE_CLASSIFIER = new GenerationFailureClassifier();
    private static final int PRECISE_HTML_MAX_ATTEMPTS = 1;

    private final LlmProvider llmProvider;
    private final HtmlPreciseEditor htmlPreciseEditor;
    private final GenerationEngine generationEngine;
    private final GeneratedContentGate generatedContentGate;
    private final PatchPayloadRepairSupport patchPayloadRepairSupport;
    private final ExternalizedRuntimeHostNormalizer externalizedRuntimeHostNormalizer;
    private final PatchExecutionSupport executionSupport;

    PreciseHtmlPatchExecutor(
            LlmProvider llmProvider,
            HtmlPreciseEditor htmlPreciseEditor,
            GenerationEngine generationEngine,
            GeneratedContentGate generatedContentGate,
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            ExternalizedRuntimeHostNormalizer externalizedRuntimeHostNormalizer,
            PatchExecutionSupport executionSupport
    ) {
        this.llmProvider = llmProvider;
        this.htmlPreciseEditor = htmlPreciseEditor;
        this.generationEngine = generationEngine;
        this.generatedContentGate = generatedContentGate;
        this.patchPayloadRepairSupport = patchPayloadRepairSupport;
        this.externalizedRuntimeHostNormalizer = externalizedRuntimeHostNormalizer;
        this.executionSupport = executionSupport;
    }

    String generate(HostHtmlPatchRequest request) {
        PatchGenerationPrompt generationPrompt = HtmlPatchPromptAssembler.preciseHtmlPrompt(
                request.relativePath(),
                request.planSummary(),
                request.taskPackageMarkdown(),
                executionSupport.nullToEmpty(request.coderContextMarkdown()),
                request.reason(),
                executionSupport.nullToEmpty(request.feedback()),
                htmlPreciseEditor.describeAnchors(request.existingContent()),
                request.targetedContext(),
                executionSupport.summarizeForVerification(
                        request.existingContent(),
                        GenerationBudgetProfile.fileContextPreviewChars()
                )
        );
        String generationSystem = generationPrompt.systemPrompt();
        String generationUser = generationPrompt.userPrompt();
        try {
            return generationEngine.execute(new GenerationSpec<>(
                    FileEditStrategyNames.operation(FileEditStrategyNames.PRECISE_HTML, request.relativePath()),
                    PRECISE_HTML_MAX_ATTEMPTS,
                    GenerationExecutionPolicy.defaultHeartbeatInterval(),
                    executionSupport.generationObserver(
                            request.relativePath(),
                            FileEditStrategyNames.PRECISE_HTML,
                            request.deliveryMode(),
                            request.eventJournal()
                    ),
                    (attempt, retryFeedback) -> {
                        String prompt = RetryPromptComposer.append(generationUser, retryFeedback, "上一轮精确改写失败，请修正后重新生成：");
                        String generated = patchPayloadRepairSupport.normalizeGeneratedPayload(
                                request.relativePath(),
                                llmProvider.generate(
                                        generationSystem,
                                        prompt,
                                        LlmOptions.outputBudgetRatio(GenerationBudgetProfile.preciseHtmlOutputRatio()),
                                        ModelRole.IMPLEMENTATION
                                )
                        );
                        HtmlPrecisePatch patch = patchPayloadRepairSupport.readStructuredPayload(
                                request.relativePath(),
                                FileEditStrategyNames.PRECISE_HTML,
                                generated,
                                HtmlPrecisePatch.class,
                                request.eventJournal()
                        );
                        String merged = htmlPreciseEditor.applyPatch(request.existingContent(), patch);
                        merged = externalizedRuntimeHostNormalizer.normalize(request.relativePath(), merged);
                        GateReport validationReport = generatedContentGate.evaluate(
                                new GeneratedContentGateInput(request.projectPath(), request.relativePath(), merged)
                        );
                        if (validationReport.passed()) {
                            return GenerationAttemptResult.success(merged);
                        }
                        String validationFailure = generatedContentGate.renderFailure(validationReport);
                        return GenerationAttemptResult.failure(
                                generatedContentGate.failureTypeFor(validationReport),
                                validationFailure,
                                HtmlPatchFeedbackRenderer.validationRetryFeedback(
                                        attempt,
                                        request.relativePath(),
                                        validationFailure
                                )
                        );
                    },
                    GENERATION_FAILURE_CLASSIFIER::classify,
                    (failureType, evidence) -> executionSupport.generationFailure(
                            request.relativePath(),
                            request.deliveryMode(),
                            FileEditStrategyNames.PRECISE_HTML,
                            PRECISE_HTML_MAX_ATTEMPTS,
                            failureType,
                            evidence,
                            HtmlPatchFeedbackRenderer.generationFailureAdvice(failureType)
                    ),
                    llmProvider::consumeLastTelemetry
            ));
        } catch (GenerationFailureException exception) {
            throw exception;
        }
    }
}
