package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import devflow.agent.executor.context.OutputBudgetCalculator;
import devflow.agent.executor.context.OutputBudgetDecision;
import devflow.agent.executor.context.PromptTokenEstimator;
import devflow.agent.executor.generation.GenerationBudgetProperties;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.ModelBudgetRegistry;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputBudgetCalculatorTests {

    @Test
    void capsRequestedOutputByModelWindowAndReserve() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(2_048, 1.0d, 0.125d, 256, 256, 160, 4.0d),
                Map.of("tiny-model", new GenerationBudgetProperties.Override(2_048, 1.0d, 0.125d, 256, 256, 160, 4.0d))
        );
        OutputBudgetCalculator calculator = new OutputBudgetCalculator(
                new ModelBudgetRegistry(properties),
                new PromptTokenEstimator()
        );

        String prompt = "x".repeat(5_000);
        Map<String, Object> adjusted = calculator.applyOutputBudget(
                "tiny-model",
                "system",
                prompt,
                LlmOptions.numPredict(1_800)
        );

        int numPredict = LlmOptions.readNumPredict(adjusted).orElseThrow();
        assertTrue(numPredict < 1_800);
        assertEquals(540, numPredict);
        assertEquals(2_048, LlmOptions.readNumCtx(adjusted).orElseThrow());
    }

    @Test
    void neverDropsBelowMinimumOutputBudget() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(1_024, 0.25d, 0.87890625d, 900, 900, 160, 3.0d),
                Map.of()
        );
        OutputBudgetCalculator calculator = new OutputBudgetCalculator(
                new ModelBudgetRegistry(properties),
                new PromptTokenEstimator()
        );

        Map<String, Object> adjusted = calculator.applyOutputBudget(
                "unknown-model",
                "system",
                "x".repeat(20_000),
                LlmOptions.numPredict(32)
        );

        assertEquals(160, LlmOptions.readNumPredict(adjusted).orElseThrow());
        assertEquals(1_024, LlmOptions.readNumCtx(adjusted).orElseThrow());
    }

    @Test
    void reserveScalesWithContextWindowWithinBounds() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(65_536, 0.25d, 0.05d, 1_024, 8_192, 160, 4.0d),
                Map.of()
        );
        OutputBudgetCalculator calculator = new OutputBudgetCalculator(
                new ModelBudgetRegistry(properties),
                new PromptTokenEstimator()
        );

        OutputBudgetDecision decision = calculator.calculateOutputBudget(
                "unknown-model",
                "system",
                "user",
                LlmOptions.numPredict(9_000)
        );

        assertEquals(65_536, decision.contextWindowTokens());
        assertEquals(3_277, decision.reserveTokens());
        assertEquals(3_437, decision.outputReserveTokens());
        assertTrue(decision.fixedTokens() > 0);
        assertTrue(decision.retrievedTokens() > 0);
        assertTrue(decision.materialBudgetTokens() < 65_536);
        assertTrue(decision.availableOutputTokens() <= 65_536 - 3_277);
        assertTrue(decision.effectiveOutputTokens() <= Math.round(decision.availableOutputTokens() * 0.25d));
    }

    @Test
    void budgetRatioUsesDynamicCeilingInsteadOfFixedNumPredict() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(2_048, 1.0d, 0.125d, 256, 256, 160, 4.0d),
                Map.of("tiny-model", new GenerationBudgetProperties.Override(2_048, 1.0d, 0.125d, 256, 256, 160, 4.0d))
        );
        OutputBudgetCalculator calculator = new OutputBudgetCalculator(
                new ModelBudgetRegistry(properties),
                new PromptTokenEstimator()
        );

        OutputBudgetDecision decision = calculator.calculateOutputBudget(
                "tiny-model",
                "system",
                "x".repeat(5_000),
                LlmOptions.outputBudgetRatio(1.0d)
        );

        assertEquals(540, decision.requestedOutputTokens());
        assertEquals(540, decision.effectiveOutputTokens());
    }

    @Test
    void budgetRatioIsFurtherCappedByExplicitNumPredictWhenBothExist() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(4_096, 1.0d, 0.05d, 256, 256, 160, 4.0d),
                Map.of()
        );
        OutputBudgetCalculator calculator = new OutputBudgetCalculator(
                new ModelBudgetRegistry(properties),
                new PromptTokenEstimator()
        );

        OutputBudgetDecision decision = calculator.calculateOutputBudget(
                "unknown-model",
                "system",
                "small prompt",
                LlmOptions.withNumPredict(LlmOptions.outputBudgetRatio(1.0d), 640)
        );

        assertEquals(640, decision.requestedOutputTokens());
        assertEquals(640, decision.effectiveOutputTokens());
    }
}
