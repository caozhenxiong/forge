package devflow.agent.executor;

import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.editing.HtmlPrecisePatch;
import java.util.Locale;

/**
 * 聚焦区域 HTML patch 执行器。
 *
 * <p>只负责 focused markup/style/script 三类区域的 patch 执行。
 */
final class FocusedRegionHtmlPatchExecutor {

    private static final GenerationFailureClassifier GENERATION_FAILURE_CLASSIFIER = new GenerationFailureClassifier();

    private final LlmProvider llmProvider;
    private final HtmlPreciseEditor htmlPreciseEditor;
    private final GenerationEngine generationEngine;
    private final GeneratedContentGate generatedContentGate;
    private final GeneratedPayloadSupport generatedPayloadSupport;
    private final FocusedHtmlRegionNormalizer focusedHtmlRegionNormalizer;
    private final HtmlFocusedRegionResolver htmlFocusedRegionResolver;
    private final ExternalizedRuntimeHostNormalizer externalizedRuntimeHostNormalizer;
    private final int maxFileGenerationAttempts;
    private final PatchExecutionSupport executionSupport;

    FocusedRegionHtmlPatchExecutor(
            LlmProvider llmProvider,
            HtmlPreciseEditor htmlPreciseEditor,
            GenerationEngine generationEngine,
            GeneratedContentGate generatedContentGate,
            GeneratedPayloadSupport generatedPayloadSupport,
            FocusedHtmlRegionNormalizer focusedHtmlRegionNormalizer,
            HtmlFocusedRegionResolver htmlFocusedRegionResolver,
            ExternalizedRuntimeHostNormalizer externalizedRuntimeHostNormalizer,
            int maxFileGenerationAttempts,
            PatchExecutionSupport executionSupport
    ) {
        this.llmProvider = llmProvider;
        this.htmlPreciseEditor = htmlPreciseEditor;
        this.generationEngine = generationEngine;
        this.generatedContentGate = generatedContentGate;
        this.generatedPayloadSupport = generatedPayloadSupport;
        this.focusedHtmlRegionNormalizer = focusedHtmlRegionNormalizer;
        this.htmlFocusedRegionResolver = htmlFocusedRegionResolver;
        this.externalizedRuntimeHostNormalizer = externalizedRuntimeHostNormalizer;
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
        this.executionSupport = executionSupport;
    }

    String generate(HostHtmlPatchRequest request, HtmlEditRegion preferredRegion) {
        HtmlEditRegion region = htmlFocusedRegionResolver.resolvePreferredRegion(request.existingContent(), preferredRegion);
        PatchGenerationPrompt generationPrompt = HtmlPatchPromptAssembler.focusedRegionPrompt(
                request.relativePath(),
                region,
                request.planSummary(),
                request.taskPackageMarkdown(),
                executionSupport.nullToEmpty(request.coderContextMarkdown()),
                request.reason(),
                executionSupport.nullToEmpty(request.feedback()),
                request.targetedContext(),
                executionSupport.summarizeForVerification(
                        request.existingContent(),
                        GenerationBudgetProfile.fileContextPreviewChars()
                )
        );
        String system = generationPrompt.systemPrompt();
        String user = generationPrompt.userPrompt();
        try {
            return generationEngine.execute(new GenerationSpec<>(
                    FileEditStrategyNames.operation(
                            FileEditStrategyNames.FOCUSED_HTML_REGION,
                            request.relativePath(),
                            region.name().toLowerCase(Locale.ROOT)
                    ),
                    maxFileGenerationAttempts,
                    GenerationExecutionPolicy.defaultHeartbeatInterval(),
                    executionSupport.generationObserver(
                            request.relativePath(),
                            FileEditStrategyNames.focusedHtmlRegion(region),
                            request.deliveryMode(),
                            request.eventJournal()
                    ),
                    (attempt, retryFeedback) -> {
                        String prompt = RetryPromptComposer.append(user, retryFeedback, "上一轮聚焦区块改写不可接受，请修正后重新生成：");
                        String generated = generatedPayloadSupport.normalizeGeneratedPayload(
                                request.relativePath(),
                                llmProvider.generate(
                                        system,
                                        prompt,
                                        LlmOptions.outputBudgetRatio(focusedRegionOutputRatio(region)),
                                        ModelRole.IMPLEMENTATION
                                )
                        );
                        HtmlPrecisePatch patch = focusedHtmlRegionNormalizer.toPatch(region, generated);
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
                                HtmlPatchFeedbackRenderer.focusedRegionValidationRetryFeedback(
                                        attempt,
                                        request.relativePath(),
                                        region,
                                        validationFailure
                                )
                        );
                    },
                    GENERATION_FAILURE_CLASSIFIER::classify,
                    (failureType, evidence) -> executionSupport.generationFailure(
                            request.relativePath(),
                            request.deliveryMode(),
                            FileEditStrategyNames.focusedHtmlRegion(region),
                            maxFileGenerationAttempts,
                            failureType,
                            evidence,
                            HtmlPatchFeedbackRenderer.focusedRegionFailureAdvice()
                    ),
                    llmProvider::consumeLastTelemetry
            ));
        } catch (GenerationFailureException exception) {
            throw exception;
        }
    }

    private double focusedRegionOutputRatio(HtmlEditRegion region) {
        if (region == HtmlEditRegion.SCRIPT) {
            return GenerationBudgetProfile.focusedScriptOutputRatio();
        }
        if (region == HtmlEditRegion.STYLE) {
            return GenerationBudgetProfile.focusedStyleOutputRatio();
        }
        return GenerationBudgetProfile.focusedMarkupOutputRatio();
    }
}
