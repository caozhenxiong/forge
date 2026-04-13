package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeWorkingSetPolicyTests {

    @Test
    void valueCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.runtime-working-set.max-adjacent-files", "6");
        try {
            assertEquals(6, RuntimeWorkingSetPolicy.maxAdjacentRuntimeFiles());
        } finally {
            System.clearProperty("devflow.runtime-working-set.max-adjacent-files");
        }
    }
}
