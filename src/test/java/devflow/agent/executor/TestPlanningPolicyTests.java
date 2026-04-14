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
import devflow.agent.executor.testing.TestPlanningPolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPlanningPolicyTests {

    @Test
    void valuesAreConfiguredThroughTypedPropertiesObject() {
        TestPlanningPolicy policy = new TestPlanningPolicy(180, 260, 320, 1400, 0.9d);

        assertEquals(180, policy.defaultStepWaitMs());
        assertEquals(260, policy.strengthenedInteractionWaitMs());
        assertEquals(320, policy.observedInteractionWaitMs());
    }
}
