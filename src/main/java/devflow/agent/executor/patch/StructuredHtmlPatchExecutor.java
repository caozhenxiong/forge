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
import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationSpec;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

import devflow.agent.editing.HtmlDocumentAssembler;
import devflow.agent.editing.HtmlDocumentDraft;
import java.nio.file.Path;
import java.util.List;

/**
 * 结构化 HTML 草稿执行器。
 *
 * <p>只负责 `structured-html` 路径的 prompt、生成、assemble 和 gate，
 * 避免宿主 HTML 执行器继续同时维护三条完全不同的执行循环。
 */
public final class StructuredHtmlPatchExecutor {

    private static final GenerationFailureClassifier GENERATION_FAILURE_CLASSIFIER = new GenerationFailureClassifier();

    private final LlmProvider llmProvider;
    private final HtmlDocumentAssembler htmlDocumentAssembler;
    private final GenerationEngine generationEngine;
    private final GeneratedContentGate generatedContentGate;
    private final GeneratedPayloadSupport generatedPayloadSupport;
    private final int maxFileGenerationAttempts;
    private final PatchExecutionSupport executionSupport;

    public StructuredHtmlPatchExecutor(
            LlmProvider llmProvider,
            HtmlDocumentAssembler htmlDocumentAssembler,
            GenerationEngine generationEngine,
            GeneratedContentGate generatedContentGate,
            GeneratedPayloadSupport generatedPayloadSupport,
            int maxFileGenerationAttempts,
            PatchExecutionSupport executionSupport
    ) {
        this.llmProvider = llmProvider;
        this.htmlDocumentAssembler = htmlDocumentAssembler;
        this.generationEngine = generationEngine;
        this.generatedContentGate = generatedContentGate;
        this.generatedPayloadSupport = generatedPayloadSupport;
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
        this.executionSupport = executionSupport;
    }

    public String generate(HtmlTargetedRewriteRequest request) {
        PatchGenerationPrompt generationPrompt = StructuredHtmlDraftPromptAssembler.assemble(
                request.relativePath(),
                request.runtimeContract(),
                request.planSummary(),
                request.taskPackageMarkdown(),
                executionSupport.nullToEmpty(request.coderContextMarkdown()),
                request.reason(),
                executionSupport.nullToEmpty(request.feedback()),
                request.targetedContext()
        );
        String generationSystem = generationPrompt.systemPrompt();
        String generationUser = generationPrompt.userPrompt();
        try {
            return generationEngine.execute(new GenerationSpec<>(
                    FileEditStrategyNames.operation(FileEditStrategyNames.STRUCTURED_HTML, request.relativePath()),
                    maxFileGenerationAttempts,
                    GenerationExecutionPolicy.defaultHeartbeatInterval(),
                    executionSupport.generationObserver(
                            request.relativePath(),
                            FileEditStrategyNames.STRUCTURED_HTML,
                            request.deliveryMode(),
                            request.eventJournal()
                    ),
                    (attempt, retryFeedback) -> {
                        String prompt = RetryPromptComposer.append(generationUser, retryFeedback, "上一轮结构化 HTML 草稿不可接受，请修正后重新生成：");
                        String generated = generatedPayloadSupport.normalizeGeneratedPayload(
                                request.relativePath(),
                                llmProvider.generate(LlmGenerateRequest.workingPrompt(
                                        generationSystem,
                                        prompt,
                                        LlmOptions.outputBudgetRatio(GenerationBudgetProfile.preciseHtmlOutputRatio()),
                                        ModelRole.IMPLEMENTATION
                                ))
                        );
                        String assembled = assembleStructuredHtmlDocument(request.relativePath(), generated);
                        GateReport validationReport = generatedContentGate.evaluate(
                                new GeneratedContentGateInput(
                                        request.projectPath(),
                                        request.relativePath(),
                                        assembled,
                                        request.runtimeContract(),
                                        request.runtimeContract() == null ? java.util.List.of() : request.runtimeContract().runtimePaths()
                                )
                        );
                        if (validationReport.passed()) {
                            return GenerationAttemptResult.success(assembled);
                        }
                        String validationFailure = generatedContentGate.renderFailure(validationReport);
                        return GenerationAttemptResult.failure(
                                generatedContentGate.failureTypeFor(validationReport),
                                validationFailure,
                                StructuredHtmlDraftFeedbackRenderer.validationRetryFeedback(attempt, request.relativePath(), validationFailure)
                        );
                    },
                    GENERATION_FAILURE_CLASSIFIER::classify,
                    (failureType, evidence) -> executionSupport.generationFailure(
                            request.relativePath(),
                            request.deliveryMode(),
                            FileEditStrategyNames.STRUCTURED_HTML,
                            maxFileGenerationAttempts,
                            failureType,
                            evidence,
                            StructuredHtmlDraftFeedbackRenderer.generationFailureAdvice()
                    ),
                    llmProvider::consumeLastTelemetry
            ));
        } catch (GenerationFailureException exception) {
            throw exception.withEditAttemptState(new FileEditAttemptState(
                    request.relativePath(),
                    FileEditProtocolNames.TARGETED_REWRITE,
                    FileEditStrategyNames.STRUCTURED_HTML,
                    request.existingContent(),
                    "",
                    List.of(),
                    ""
            ));
        }
    }

    private String assembleStructuredHtmlDocument(Path relativePath, String generated) throws Exception {
        if (devflow.agent.parsing.HtmlDocumentInspector.hasExplicitDocumentSkeleton(generated)) {
            return generated;
        }
        HtmlDocumentDraft draft = generatedPayloadSupport.readStructuredPayload(generated, HtmlDocumentDraft.class);
        return htmlDocumentAssembler.assemble(relativePath, draft);
    }
}
