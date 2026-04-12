package devflow.agent.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImplementationExecutionPolicyTests {

    @Test
    void valuesCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.implementation.subtask-attempts", "4");
        System.setProperty("devflow.implementation.planning-payload-repair-attempts", "5");
        System.setProperty("devflow.implementation.planning-unit-attempts", "2");
        System.setProperty("devflow.implementation.file-generation-attempts", "6");
        System.setProperty("devflow.implementation.max-files-per-subtask", "3");
        System.setProperty("devflow.implementation.max-delivery-policy-files", "4");
        try {
            assertEquals(4, ImplementationExecutionPolicy.subtaskAttempts());
            assertEquals(5, ImplementationExecutionPolicy.planningPayloadRepairAttempts());
            assertEquals(2, ImplementationExecutionPolicy.planningUnitAttempts());
            assertEquals(6, ImplementationExecutionPolicy.fileGenerationAttempts());
            assertEquals(3, ImplementationExecutionPolicy.maxFilesPerSubtask());
            assertEquals(4, ImplementationExecutionPolicy.maxDeliveryPolicyFiles());
        } finally {
            System.clearProperty("devflow.implementation.subtask-attempts");
            System.clearProperty("devflow.implementation.planning-payload-repair-attempts");
            System.clearProperty("devflow.implementation.planning-unit-attempts");
            System.clearProperty("devflow.implementation.file-generation-attempts");
            System.clearProperty("devflow.implementation.max-files-per-subtask");
            System.clearProperty("devflow.implementation.max-delivery-policy-files");
        }
    }
}
