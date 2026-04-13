package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.ModelRole;
import devflow.agent.executor.llm.OllamaProperties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaPropertiesTests {

    @Test
    void defaultsToQwenCoderWhenModelIsNotSpecified() {
        OllamaProperties properties = new OllamaProperties(null, null, 0, null);

        assertEquals("qwen3-coder:30b", properties.model());
        assertEquals("qwen3-coder:30b", properties.resolveModel(ModelRole.ANALYSIS));
        assertEquals("qwen3-coder:30b", properties.resolveModel(ModelRole.CODE_REVIEW));
    }
}
