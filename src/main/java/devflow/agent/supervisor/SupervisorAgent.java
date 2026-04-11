package devflow.agent.supervisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ProjectedContext;
import devflow.agent.executor.GenerationFailureReport;
import devflow.agent.executor.GenerationBudgetProfile;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.LlmOptions;
import devflow.agent.executor.ModelRole;
import devflow.agent.executor.StructuredPayloadReader;
import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageFlowPolicy;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;
    private final ContextProjector contextProjector;
    private final StageFlowPolicy stageFlowPolicy;
    private final SupervisorFallbackPolicy supervisorFallbackPolicy;
    private final SupervisorArtifactRenderer artifactRenderer;
    private final SupervisorDecisionSanitizer decisionSanitizer;
    private final SupervisorPromptAssembler promptAssembler;
    private final StructuredPayloadReader structuredPayloadReader;

    public SupervisorAgent(
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            ContextProjector contextProjector,
            StageFlowPolicy stageFlowPolicy,
            SupervisorFallbackPolicy supervisorFallbackPolicy
    ) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
        this.contextProjector = contextProjector;
        this.stageFlowPolicy = stageFlowPolicy;
        this.supervisorFallbackPolicy = supervisorFallbackPolicy;
        this.artifactRenderer = new SupervisorArtifactRenderer();
        this.decisionSanitizer = new SupervisorDecisionSanitizer(stageFlowPolicy, supervisorFallbackPolicy);
        this.promptAssembler = new SupervisorPromptAssembler(artifactRenderer);
        this.structuredPayloadReader = new StructuredPayloadReader(objectMapper);
    }

    public SupervisorDecision decide(
            Path projectPath,
            RunRecord runRecord,
            StageType currentStage,
            ReviewResult reviewResult,
            boolean repeatedIssue
    ) {
        StageExecution currentExecution = runRecord.stageStates().get(currentStage);
        StageType nextStage = stageFlowPolicy.nextStage(currentStage);
        GatePolicy gatePolicy = runRecord.config().gatePolicies().getOrDefault(currentStage, GatePolicy.AGENT_ONLY);
        ProjectedContext projectedContext = contextProjector.project(projectPath, runRecord, currentStage);
        SupervisorDecision fallback = supervisorFallbackPolicy.decideStageFallback(
                runRecord,
                currentStage,
                gatePolicy,
                reviewResult,
                repeatedIssue,
                projectedContext
        );
        if (llmProvider == null) {
            return fallback;
        }

        try {
            String response = llmProvider.generate(
                    promptAssembler.decisionSystemPrompt(),
                    promptAssembler.decisionUserPrompt(
                            runRecord,
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
            );
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
        } catch (Exception ignored) {
            return fallback;
        }
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
        DocumentLanguage language = DocumentLanguage.detect(
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
        if (llmProvider == null) {
            return fallback;
        }

        try {
            String response = llmProvider.generate(
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
            );
            GenerationRecoveryPayload payload = structuredPayloadReader.readJsonObject(response, GenerationRecoveryPayload.class);
            return decisionSanitizer.sanitizeGenerationRecoveryDecision(payload, fallback, failureReport);
        } catch (Exception ignored) {
            return fallback;
        }
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

}
