package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 整文件重写主链执行器。
 *
 * <p>whole-file 不再是默认主路径，但在明确允许的极少数场景里，
 * 仍然需要一条独立执行链负责：
 * 1. prompt 组装；
 * 2. 模型调用；
 * 3. 本地结构校验；
 * 4. 重试与失败装配。
 *
 * <p>这样 {@link FileEditCoordinator} 只保留“是否允许走 whole-file”的路由判断，
 * 不再继续背着整条旧生成循环。
 */
final class WholeFilePatchExecutor {

    private static final GenerationFailureClassifier GENERATION_FAILURE_CLASSIFIER = new GenerationFailureClassifier();

    private final LlmProvider llmProvider;
    private final GenerationEngine generationEngine;
    private final GeneratedContentGate generatedContentGate;
    private final GeneratedPayloadSupport generatedPayloadSupport;
    private final int maxFileGenerationAttempts;
    private final PatchExecutionSupport executionSupport;

    WholeFilePatchExecutor(
            LlmProvider llmProvider,
            GenerationEngine generationEngine,
            GeneratedContentGate generatedContentGate,
            GeneratedPayloadSupport generatedPayloadSupport,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            ImplementationGenerationObserverFactory implementationGenerationObserverFactory,
            int maxFileGenerationAttempts
    ) {
        this.llmProvider = llmProvider;
        this.generationEngine = generationEngine;
        this.generatedContentGate = generatedContentGate;
        this.generatedPayloadSupport = generatedPayloadSupport;
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
        this.executionSupport = new PatchExecutionSupport(
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory
        );
    }

    String generate(WholeFilePatchRequest request) {
        PatchGenerationPrompt generationPrompt = WholeFilePromptAssembler.assemble(
                request.relativePath(),
                request.deliveryMode(),
                request.planSummary(),
                request.taskPackageMarkdown(),
                request.reason(),
                request.feedback(),
                request.targetedContext(),
                request.existingContent()
        );
        String generationSystem = generationPrompt.systemPrompt();
        String generationUser = generationPrompt.userPrompt();
        return generationEngine.execute(new GenerationSpec<>(
                FileEditStrategyNames.operation(FileEditStrategyNames.FULL_FILE, request.relativePath()),
                maxFileGenerationAttempts,
                GenerationExecutionPolicy.defaultHeartbeatInterval(),
                executionSupport.generationObserver(
                        request.relativePath(),
                        FileEditStrategyNames.FULL_FILE,
                        request.deliveryMode(),
                        request.eventJournal()
                ),
                (attempt, retryFeedback) -> {
                    String prompt = RetryPromptComposer.append(
                            generationUser,
                            retryFeedback,
                            "上一轮输出不可接受，请修正后重新生成："
                    );
                    String generated = generatedPayloadSupport.normalizeGeneratedPayload(
                            request.relativePath(),
                            llmProvider.generate(
                                    generationSystem,
                                    prompt,
                                    LlmOptions.outputBudgetRatio(GenerationBudgetProfile.wholeFileRewriteOutputRatio(request.deliveryMode())),
                                    ModelRole.IMPLEMENTATION
                            )
                    );
                    GateReport validationReport = generatedContentGate.evaluate(
                            new GeneratedContentGateInput(request.projectPath(), request.relativePath(), generated)
                    );
                    if (validationReport.passed()) {
                        return GenerationAttemptResult.success(generated);
                    }
                    String validationFailure = generatedContentGate.renderFailure(validationReport);
                    return GenerationAttemptResult.failure(
                            generatedContentGate.failureTypeFor(validationReport),
                            validationFailure,
                            WholeFileFeedbackRenderer.validationRetryFeedback(
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
                        FileEditStrategyNames.FULL_FILE,
                        maxFileGenerationAttempts,
                        failureType,
                        evidence,
                        WholeFileFeedbackRenderer.generationFailureAdvice()
                ),
                llmProvider::consumeLastTelemetry
        ));
    }
}
