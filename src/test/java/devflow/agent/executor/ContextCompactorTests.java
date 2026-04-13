package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.context.CompactedPrompt;
import devflow.agent.executor.context.ContextBudgetPlanner;
import devflow.agent.executor.context.ContextCompactor;
import devflow.agent.executor.context.PromptTokenEstimator;
import devflow.agent.executor.generation.GenerationBudgetProperties;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.ModelBudgetRegistry;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextCompactorTests {

    @Test
    void leavesPromptUntouchedWhenBudgetIsEnough() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(8_192, 0.25d, 0.1220703125d, 1_000, 1_000, 160, 4.0d),
                Map.of()
        );
        ContextCompactor compactor = new ContextCompactor(
                new ContextBudgetPlanner(
                        new ModelBudgetRegistry(properties),
                        new PromptTokenEstimator()
                )
        );

        CompactedPrompt prompt = compactor.compact(
                "any-model",
                LlmGenerateRequest.workingPrompt("system", "user", Map.of(), null)
        );

        assertEquals("system", prompt.systemPrompt());
        assertEquals("user", prompt.userPrompt());
        assertTrue(!prompt.budgetPlan().compactRequired());
    }

    @Test
    void compactsMiddleWhenPromptExceedsBudget() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(1_024, 0.25d, 0.1953125d, 200, 200, 120, 2.0d),
                Map.of()
        );
        ContextCompactor compactor = new ContextCompactor(
                new ContextBudgetPlanner(
                        new ModelBudgetRegistry(properties),
                        new PromptTokenEstimator()
                )
        );

        String system = "S".repeat(1_000);
        String user = "U".repeat(8_000);
        CompactedPrompt prompt = compactor.compact(
                "any-model",
                LlmGenerateRequest.workingPrompt(system, user, Map.of(), null)
        );

        assertTrue(prompt.budgetPlan().compactRequired());
        assertEquals(system, prompt.systemPrompt());
        assertTrue(prompt.userPrompt().length() < user.length());
        assertTrue(prompt.userPrompt().contains("...<truncated"));
    }
}
