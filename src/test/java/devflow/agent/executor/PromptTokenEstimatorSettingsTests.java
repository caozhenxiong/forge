package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.context.PromptTokenEstimatorSettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptTokenEstimatorSettingsTests {

    @Test
    void defaultsCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.prompt-estimator.min-chars-per-token", "2.2");
        System.setProperty("devflow.prompt-estimator.max-chars-per-token", "7.5");
        System.setProperty("devflow.prompt-estimator.learning-rate", "0.5");
        try {
            PromptTokenEstimatorSettings settings = PromptTokenEstimatorSettings.defaults();

            assertEquals(2.2d, settings.minCharsPerToken());
            assertEquals(7.5d, settings.maxCharsPerToken());
            assertEquals(0.5d, settings.learningRate());
        } finally {
            System.clearProperty("devflow.prompt-estimator.min-chars-per-token");
            System.clearProperty("devflow.prompt-estimator.max-chars-per-token");
            System.clearProperty("devflow.prompt-estimator.learning-rate");
        }
    }
}
