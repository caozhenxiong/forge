package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StructuredReviewResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OllamaLlmProvider implements LlmProvider, ChatCapableLlmProvider {

    private final OllamaChatExecutor chatExecutor;
    private final OllamaGenerationExecutor generationExecutor;
    private final OllamaStructuredReviewExecutor structuredReviewExecutor;

    public OllamaLlmProvider(
            OllamaProperties properties,
            ObjectMapper objectMapper,
            OutputBudgetCalculator outputBudgetCalculator,
            ContextCompactor contextCompactor
    ) {
        StructuredPayloadReader structuredPayloadReader = new StructuredPayloadReader(objectMapper);
        OllamaTransportClient transportClient = new OllamaTransportClient(properties, objectMapper);
        this.chatExecutor = new OllamaChatExecutor(
                properties,
                outputBudgetCalculator,
                transportClient,
                objectMapper
        );
        this.generationExecutor = new OllamaGenerationExecutor(
                properties,
                outputBudgetCalculator,
                contextCompactor,
                transportClient
        );
        this.structuredReviewExecutor = new OllamaStructuredReviewExecutor(
                structuredPayloadReader,
                this.generationExecutor
        );
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
        return generate(systemPrompt, userPrompt, options, null);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
        return generationExecutor.generate(systemPrompt, userPrompt, options, role);
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        return chatExecutor.chat(request);
    }

    @Override
    public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
        return review(systemPrompt, candidateContent, options, null);
    }

    @Override
    public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
        return reviewStructured(systemPrompt, candidateContent, options, role).result();
    }

    @Override
    public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options) {
        return reviewStructured(systemPrompt, candidateContent, options, null);
    }

    @Override
    public StructuredReviewResult reviewStructured(
            String systemPrompt,
            String candidateContent,
            Map<String, Object> options,
            ModelRole role
    ) {
        return structuredReviewExecutor.reviewStructured(systemPrompt, candidateContent, options, role);
    }

    @Override
    public GenerationTelemetry consumeLastTelemetry() {
        GenerationTelemetry generationTelemetry = generationExecutor.consumeLastTelemetry();
        if (generationTelemetry != null) {
            return generationTelemetry;
        }
        GenerationTelemetry chatTelemetry = chatExecutor.consumeLastTelemetry();
        if (chatTelemetry != null) {
            return chatTelemetry;
        }
        return structuredReviewExecutor.consumeLastTelemetry();
    }
}
