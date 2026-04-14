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

import devflow.agent.executor.context.PromptTokenEstimator;
import devflow.agent.executor.context.PromptTokenEstimatorSettings;
import devflow.agent.executor.llm.ModelBudgetProfile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTokenEstimatorTests {

    @Test
    void estimateUsesConfiguredMinimumCharsPerTokenFloor() {
        PromptTokenEstimator estimator = new PromptTokenEstimator(
                new PromptTokenEstimatorSettings(2.0d, 6.0d, 0.25d)
        );

        int estimated = estimator.estimatePromptTokens(
                "model",
                new ModelBudgetProfile(32_768, 0.25d, 0.05d, 1_024, 4_096, 160, 1.0d),
                "abcd",
                "efgh"
        );

        assertEquals(4, estimated);
    }

    @Test
    void observePromptUsageAdjustsCalibrationWithinConfiguredBounds() {
        PromptTokenEstimator estimator = new PromptTokenEstimator(
                new PromptTokenEstimatorSettings(2.0d, 5.0d, 0.5d)
        );
        ModelBudgetProfile profile = new ModelBudgetProfile(32_768, 0.25d, 0.05d, 1_024, 4_096, 160, 4.0d);

        int before = estimator.estimatePromptTokens("model", profile, "a".repeat(40), "b".repeat(40));
        estimator.observePromptUsage("model", "a".repeat(40), "b".repeat(40), 20);
        int after = estimator.estimatePromptTokens("model", profile, "a".repeat(40), "b".repeat(40));

        assertTrue(after >= before, "校准后应该朝着更保守的 token 估算方向收敛");
        assertTrue(after <= 40, "校准结果仍应被配置边界限制");
    }
}
