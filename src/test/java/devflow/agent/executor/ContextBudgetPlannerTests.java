package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.context.ContextBudgetPlan;
import devflow.agent.executor.context.ContextBudgetPlanner;
import devflow.agent.executor.context.PromptTokenEstimator;
import devflow.agent.executor.generation.GenerationBudgetProperties;
import devflow.agent.executor.llm.ModelBudgetRegistry;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetPlannerTests {

    @Test
    void skipsCompactionWhenPromptFitsBudget() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(8_192, 0.25d, 0.1220703125d, 1_000, 1_000, 160, 4.0d),
                Map.of()
        );
        ContextBudgetPlanner planner = new ContextBudgetPlanner(
                new ModelBudgetRegistry(properties),
                new PromptTokenEstimator()
        );

        ContextBudgetPlan plan = planner.plan("any-model", "system", "user");

        assertFalse(plan.compactRequired());
    }

    @Test
    void compactionPlanPreservesBothNonEmptyPromptParts() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(1_024, 0.25d, 0.1953125d, 200, 200, 120, 2.0d),
                Map.of()
        );
        ContextBudgetPlanner planner = new ContextBudgetPlanner(
                new ModelBudgetRegistry(properties),
                new PromptTokenEstimator()
        );

        ContextBudgetPlan plan = planner.plan("any-model", "S".repeat(4_000), "U".repeat(8_000));

        assertTrue(plan.compactRequired());
        assertTrue(plan.fixedTokens() > 0);
        assertTrue(plan.retrievedTokens() > 0);
        assertTrue(plan.outputReserveTokens() > 0);
        assertTrue(plan.materialBudgetTokens() > 0);
        assertTrue(plan.systemCharBudget() > 0);
        assertTrue(plan.userCharBudget() > 0);
    }
}
