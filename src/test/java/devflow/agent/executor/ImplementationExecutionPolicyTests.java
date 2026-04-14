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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImplementationExecutionPolicyTests {

    @Test
    void valuesAreConfiguredThroughTypedPropertiesObject() {
        ImplementationExecutionPolicy policy = new ImplementationExecutionPolicy(
                4,
                5,
                2,
                6,
                12,
                3,
                4,
                30_000L,
                120_000L
        );

        assertEquals(4, policy.subtaskAttempts());
        assertEquals(5, policy.planningPayloadRepairAttempts());
        assertEquals(2, policy.planningUnitAttempts());
        assertEquals(6, policy.fileGenerationAttempts());
        assertEquals(3, policy.maxFilesPerSubtask());
        assertEquals(4, policy.maxDeliveryPolicyFiles());
    }
}
