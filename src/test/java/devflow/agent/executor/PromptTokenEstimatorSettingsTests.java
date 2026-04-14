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

import devflow.agent.executor.context.PromptTokenEstimatorSettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptTokenEstimatorSettingsTests {

    @Test
    void valuesAreConfiguredThroughTypedPropertiesObject() {
        PromptTokenEstimatorSettings settings = new PromptTokenEstimatorSettings(2.2d, 7.5d, 0.5d);

        assertEquals(2.2d, settings.minCharsPerToken());
        assertEquals(7.5d, settings.maxCharsPerToken());
        assertEquals(0.5d, settings.learningRate());
    }
}
