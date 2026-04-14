package devflow.agent.supervisor;

import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.generation.GenerationFailureReport;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;
import devflow.agent.executor.llm.StructuredPayloadReader;

import devflow.agent.context.ContextProjector;
import devflow.agent.context.ProjectedContext;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.domain.GatePolicy;
import devflow.agent.domain.RunRecord;
import devflow.agent.orchestrator.StageFlowPolicy;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageType;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 负责 supervisor 层的最终调度。
 *
 * <p>当前类刻意保持“薄”：
 * 1. 先拿到确定性的 fallback
 * 2. 再决定是否调用模型
 * 3. 调用后交给 sanitizer 清洗结果
 *
 * <p>prompt 组装、产物渲染、默认兜底都被拆到了独立组件里，
 * 这样这里就不再继续演变成新的全能类。
 */
@Component
public class SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    private final LlmProvider llmProvider;
    private final ContextProjector contextProjector;
    private final StageFlowPolicy stageFlowPolicy;
    private final SupervisorFallbackPolicy supervisorFallbackPolicy;
    private final SupervisorArtifactRenderer artifactRenderer;
    private final SupervisorDecisionSanitizer decisionSanitizer;
    private final SupervisorPromptAssembler promptAssembler;
    private final StructuredPayloadReader structuredPayloadReader;
    private final LanguagePolicy languagePolicy;

    public SupervisorAgent(
            LlmProvider llmProvider,
            ContextProjector contextProjector,
            StageFlowPolicy stageFlowPolicy,
            SupervisorFallbackPolicy supervisorFallbackPolicy,
            SupervisorArtifactRenderer artifactRenderer,
            SupervisorDecisionSanitizer decisionSanitizer,
            SupervisorPromptAssembler promptAssembler,
            StructuredPayloadReader structuredPayloadReader,
            LanguagePolicy languagePolicy
    ) {
        this.llmProvider = llmProvider;
        this.contextProjector = contextProjector;
        this.stageFlowPolicy = stageFlowPolicy;
        this.supervisorFallbackPolicy = supervisorFallbackPolicy;
        this.artifactRenderer = artifactRenderer;
        this.decisionSanitizer = decisionSanitizer;
        this.promptAssembler = promptAssembler;
        this.structuredPayloadReader = structuredPayloadReader;
        this.languagePolicy = languagePolicy;
    }

    public SupervisorDecision decide(
            RunRecord runRecord,
            StageType currentStage,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            ProjectedContext projectedContext
    ) {
        StageExecution currentExecution = runRecord.stageStates().get(currentStage);
        StageType nextStage = stageFlowPolicy.nextStage(currentStage);
        GatePolicy gatePolicy = runRecord.config().gatePolicies().getOrDefault(currentStage, GatePolicy.AGENT_ONLY);
        DocumentLanguage language = languagePolicy.resolve(runRecord.goal(), runRecord.constraints());
        SupervisorDecision fallback = supervisorFallbackPolicy.decideStageFallback(
                runRecord,
                currentStage,
                gatePolicy,
                reviewResult,
                repeatedIssue,
                projectedContext
        );
        return resolveWithFallback(
                fallback,
                () -> llmProvider.generate(LlmGenerateRequest.workingPrompt(
                        promptAssembler.decisionSystemPrompt(),
                        promptAssembler.decisionUserPrompt(
                                runRecord,
                                language,
                                currentStage,
                                nextStage,
                                gatePolicy,
                                currentExecution,
                                reviewResult,
                                repeatedIssue,
                                projectedContext,
                                fallback
                        ),
                        LlmOptions.outputBudgetRatio(GenerationBudgetProfile.supervisorDecisionOutputRatio()),
                        ModelRole.SUPERVISOR
                )),
                response -> {
                    DecisionPayload payload = structuredPayloadReader.readJsonObject(response, DecisionPayload.class);
                    return decisionSanitizer.sanitizeDecision(
                            payload,
                            runRecord,
                            currentStage,
                            nextStage,
                            gatePolicy,
                            reviewResult,
                            repeatedIssue,
                            fallback,
                            projectedContext
                    );
                },
                "Supervisor decision failed, using fallback. stage={}",
                currentStage
        );
    }

    public GenerationRecoveryDecision decideGenerationFailure(
            Path projectPath,
            RunRecord runRecord,
            GenerationFailureReport failureReport,
            int subtaskAttempt,
            String subtaskTitle,
            String subtaskGoal,
            String feedback,
            DeliveryPolicy currentPolicy
    ) {
        ProjectedContext projectedContext = contextProjector.project(projectPath, runRecord, StageType.IMPLEMENTATION);
        DocumentLanguage language = languagePolicy.resolve(
                runRecord.goal(),
                runRecord.constraints(),
                feedback,
                failureReport == null ? "" : failureReport.summary()
        );
        GenerationRecoveryDecision fallback = supervisorFallbackPolicy.decideGenerationFallback(
                failureReport,
                subtaskAttempt,
                currentPolicy
        );
        return resolveWithFallback(
                fallback,
                () -> llmProvider.generate(LlmGenerateRequest.workingPrompt(
                        promptAssembler.generationRecoverySystemPrompt(),
                        promptAssembler.generationRecoveryUserPrompt(
                                runRecord,
                                failureReport,
                                subtaskAttempt,
                                subtaskTitle,
                                subtaskGoal,
                                feedback,
                                projectedContext,
                                language,
                                fallback
                        ),
                        LlmOptions.outputBudgetRatio(GenerationBudgetProfile.generationRecoveryOutputRatio()),
                        ModelRole.SUPERVISOR
                )),
                response -> {
                    GenerationRecoveryPayload payload = structuredPayloadReader.readJsonObject(response, GenerationRecoveryPayload.class);
                    return decisionSanitizer.sanitizeGenerationRecoveryDecision(payload, fallback, failureReport);
                },
                "Generation recovery decision failed, using fallback. attempt={}",
                subtaskAttempt
        );
    }

    public String renderDecisionArtifact(
            StageType currentStage,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision decision,
            DocumentLanguage language
    ) {
        return artifactRenderer.renderDecisionArtifact(currentStage, reviewResult, repeatedIssue, decision, language);
    }

    private <T> T resolveWithFallback(
            T fallback,
            Supplier<String> requestSupplier,
            Function<String, T> responseHandler,
            String failureLogMessage,
            Object... failureLogArgs
    ) {
        if (llmProvider == null) {
            return fallback;
        }
        try {
            return responseHandler.apply(requestSupplier.get());
        } catch (Exception ex) {
            Object[] logArgs = Arrays.copyOf(failureLogArgs, failureLogArgs.length + 1);
            logArgs[logArgs.length - 1] = ex;
            log.warn(failureLogMessage, logArgs);
            return fallback;
        }
    }

}
