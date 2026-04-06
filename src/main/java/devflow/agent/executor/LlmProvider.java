package devflow.agent.executor;

import devflow.agent.review.ReviewResult;
import java.util.Map;

public interface LlmProvider {

    String generate(String systemPrompt, String userPrompt, Map<String, Object> options);

    ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options);

    default String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
        return generate(systemPrompt, userPrompt, options);
    }

    default ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
        return review(systemPrompt, candidateContent, options);
    }
}
