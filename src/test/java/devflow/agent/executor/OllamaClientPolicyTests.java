package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.OllamaClientPolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaClientPolicyTests {

    @Test
    void valuesCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.ollama.connect-timeout-seconds", "12");
        System.setProperty("devflow.ollama.max-empty-response-retries", "5");
        try {
            assertEquals(12, OllamaClientPolicy.connectTimeout().toSeconds());
            assertEquals(5, OllamaClientPolicy.maxEmptyResponseRetries());
        } finally {
            System.clearProperty("devflow.ollama.connect-timeout-seconds");
            System.clearProperty("devflow.ollama.max-empty-response-retries");
        }
    }
}
