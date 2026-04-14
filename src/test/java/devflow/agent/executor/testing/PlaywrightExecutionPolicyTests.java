package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaywrightExecutionPolicyTests {

    @Test
    void valuesAreConfiguredThroughTypedPropertiesObject() {
        PlaywrightExecutionPolicy policy = new PlaywrightExecutionPolicy(45, 30);

        assertEquals(45, policy.caseExecutionTimeout().toSeconds());
        assertEquals(30, policy.snapshotTimeout().toSeconds());
    }
}
