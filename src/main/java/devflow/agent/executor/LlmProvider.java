package devflow.agent.executor;

import devflow.agent.review.ReviewSemantics;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StructuredReviewResult;
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

    /**
     * 结构化 review 是 review 模块后续收敛的主路径。
     *
     * <p>默认实现先复用旧的 {@link #review(String, String, Map)}，再补一个空语义，
     * 这样可以在不打断旧调用方和测试桩的前提下逐步迁移到结构化语义输出。
     */
    default StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options) {
        return new StructuredReviewResult(review(systemPrompt, candidateContent, options), ReviewSemantics.empty());
    }

    default StructuredReviewResult reviewStructured(
            String systemPrompt,
            String candidateContent,
            Map<String, Object> options,
            ModelRole role
    ) {
        return reviewStructured(systemPrompt, candidateContent, options);
    }

    default GenerationTelemetry consumeLastTelemetry() {
        return null;
    }
}
