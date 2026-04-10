package devflow.agent.executor;

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

        CompactedPrompt prompt = compactor.compact("any-model", "system", "user");

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

        String system = "S".repeat(4_000);
        String user = "U".repeat(8_000);
        CompactedPrompt prompt = compactor.compact("any-model", system, user);

        assertTrue(prompt.budgetPlan().compactRequired());
        assertTrue(prompt.systemPrompt().length() < system.length());
        assertTrue(prompt.userPrompt().length() < user.length());
        assertTrue(prompt.systemPrompt().contains("...<truncated"));
        assertTrue(prompt.userPrompt().contains("...<truncated"));
    }
}
