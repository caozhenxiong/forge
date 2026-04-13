package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationTelemetry;

import devflow.agent.review.ReviewResult;
import devflow.agent.review.StructuredReviewResult;
import java.util.Map;

public interface LlmProvider {

    String generate(String systemPrompt, String userPrompt, Map<String, Object> options);

    default ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
        return reviewStructured(systemPrompt, candidateContent, options).result();
    }

    default String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
        return generate(systemPrompt, userPrompt, options);
    }

    default ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options, ModelRole role) {
        return reviewStructured(systemPrompt, candidateContent, options, role).result();
    }

    /**
     * 结构化 review 是唯一主路径。
     *
     * <p>如果某个 provider 没有实现 structured review，就必须显式失败，
     * 不能再从非结构化 review 结果反推结构化语义。
     */
    default StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options) {
        throw new UnsupportedOperationException("Structured review is required for this provider");
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
