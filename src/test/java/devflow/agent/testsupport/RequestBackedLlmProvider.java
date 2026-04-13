package devflow.agent.testsupport;

import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;
import java.util.Map;

public abstract class RequestBackedLlmProvider implements LlmProvider {

    @Override
    public String generate(LlmGenerateRequest request) {
        return generate(
                request.systemPrompt(),
                request.userPrompt(),
                request.options(),
                request.role()
        );
    }

    public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
        return generate(systemPrompt, userPrompt, options, null);
    }

    public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
        return generate(systemPrompt, userPrompt, options);
    }
}
