package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.testing.TestPlanningPolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPlanningPolicyTests {

    @Test
    void valuesCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.test-planning.default-step-wait-ms", "180");
        System.setProperty("devflow.test-planning.strengthened-interaction-wait-ms", "260");
        System.setProperty("devflow.test-planning.observed-interaction-wait-ms", "320");
        try {
            assertEquals(180, TestPlanningPolicy.defaultStepWaitMs());
            assertEquals(260, TestPlanningPolicy.strengthenedInteractionWaitMs());
            assertEquals(320, TestPlanningPolicy.observedInteractionWaitMs());
        } finally {
            System.clearProperty("devflow.test-planning.default-step-wait-ms");
            System.clearProperty("devflow.test-planning.strengthened-interaction-wait-ms");
            System.clearProperty("devflow.test-planning.observed-interaction-wait-ms");
        }
    }
}
