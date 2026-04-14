package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeWorkingSetPolicyTests {

    @Test
    void valueIsConfiguredThroughTypedPropertiesObject() {
        RuntimeWorkingSetPolicy policy = new RuntimeWorkingSetPolicy(6);

        assertEquals(6, policy.maxAdjacentRuntimeFiles());
    }
}
