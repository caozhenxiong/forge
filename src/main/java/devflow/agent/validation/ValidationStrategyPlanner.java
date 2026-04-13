package devflow.agent.validation;

import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;
import devflow.agent.executor.llm.StructuredPayloadReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class ValidationStrategyPlanner {

    private final LlmProvider llmProvider;
    private final StructuredPayloadReader structuredPayloadReader;
    private final ValidationCapabilityCandidateBuilder candidateBuilder;
    private final ValidationPlanningPromptBuilder promptBuilder;
    private final ValidationPlanSanitizer planSanitizer;

    public ValidationStrategyPlanner(LlmProvider llmProvider, ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.structuredPayloadReader = new StructuredPayloadReader(objectMapper);
        this.candidateBuilder = new ValidationCapabilityCandidateBuilder();
        this.promptBuilder = new ValidationPlanningPromptBuilder();
        this.planSanitizer = new ValidationPlanSanitizer();
    }

    public ValidationPlan plan(ProjectFingerprint fingerprint) {
        List<ValidationStep> candidates = candidateBuilder.build(fingerprint);
        ValidationPlan deterministicPlan = planSanitizer.buildDeterministicPlan(fingerprint, candidates);
        if (llmProvider == null || candidates.isEmpty()) {
            return deterministicPlan;
        }

        try {
            String response = llmProvider.generate(
                    promptBuilder.systemPrompt(),
                    promptBuilder.userPrompt(fingerprint, candidates),
                    devflow.agent.executor.llm.LlmOptions.outputBudgetRatio(GenerationBudgetProfile.validationStrategyOutputRatio()),
                    ModelRole.VALIDATION_STRATEGY
            );
            ValidationPlanningPayload payload = structuredPayloadReader.readJsonObject(response, ValidationPlanningPayload.class);
            ValidationPlan planned = planSanitizer.sanitize(payload, candidates);
            if (planned != null) {
                return planned;
            }
        } catch (Exception ignored) {
        }

        return deterministicPlan;
    }
}
