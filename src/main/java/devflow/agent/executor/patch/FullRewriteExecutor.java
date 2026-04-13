package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationAttemptResult;
import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.generation.GenerationExecutionPolicy;
import devflow.agent.executor.generation.GenerationFailureClassifier;
import devflow.agent.executor.generation.GenerationSpec;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

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
 * <p>这样 whole-file 生成链保持独立，
 * 不再把整条生成循环重新揉回上层路由。
 */
public final class FullRewriteExecutor {

    private static final GenerationFailureClassifier GENERATION_FAILURE_CLASSIFIER = new GenerationFailureClassifier();

    private final LlmProvider llmProvider;
    private final GenerationEngine generationEngine;
    private final GeneratedContentGate generatedContentGate;
    private final GeneratedPayloadSupport generatedPayloadSupport;
    private final int maxFileGenerationAttempts;
    private final PatchExecutionSupport executionSupport;

    public FullRewriteExecutor(
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

    public String generate(FullRewriteRequest request) {
        requireSnapshot(request);
        PatchGenerationPrompt generationPrompt = FullRewritePromptAssembler.assemble(
                request.relativePath(),
                request.deliveryMode(),
                request.runtimeContract(),
                request.planSummary(),
                request.taskPackageMarkdown(),
                request.reason(),
                request.feedback(),
                request.targetedContext(),
                request.existingContent(),
                request.fileSnapshot()
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
                            llmProvider.generate(LlmGenerateRequest.workingPrompt(
                                    generationSystem,
                                    prompt,
                                    LlmOptions.outputBudgetRatio(GenerationBudgetProfile.wholeFileRewriteOutputRatio(request.deliveryMode())),
                                    ModelRole.IMPLEMENTATION
                            ))
                    );
                    GateReport validationReport = generatedContentGate.evaluate(
                            new GeneratedContentGateInput(
                                    request.projectPath(),
                                    request.relativePath(),
                                    generated,
                                    request.runtimeContract(),
                                    request.runtimeContract() == null ? java.util.List.of() : request.runtimeContract().runtimePaths()
                            )
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

    private void requireSnapshot(FullRewriteRequest request) {
        if (request == null || request.fileSnapshot() == null) {
            throw new IllegalArgumentException("Full rewrite requires an explicit file snapshot");
        }
        if (request.fileSnapshot().relativePath() == null
                || !request.fileSnapshot().relativePath().equals(request.relativePath().normalize())) {
            throw new IllegalArgumentException("Full rewrite snapshot path mismatch");
        }
    }
}
