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

import devflow.agent.executor.llm.OllamaProperties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaClientPolicyTests {

    @Test
    void clientValuesAreConfiguredThroughOllamaProperties() {
        OllamaProperties properties = new OllamaProperties(
                "http://127.0.0.1:11434",
                "qwen3-coder:30b",
                300,
                12,
                5,
                null
        );

        assertEquals(12, properties.connectTimeout().toSeconds());
        assertEquals(5, properties.maxEmptyResponseRetries());
    }
}
