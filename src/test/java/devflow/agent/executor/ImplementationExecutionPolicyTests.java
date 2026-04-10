package devflow.agent.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImplementationExecutionPolicyTests {

    @Test
    void valuesCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.implementation.subtask-attempts", "4");
        System.setProperty("devflow.implementation.plan-parse-attempts", "5");
        System.setProperty("devflow.implementation.internal-plan-retries", "2");
        System.setProperty("devflow.implementation.file-generation-attempts", "6");
        System.setProperty("devflow.implementation.max-files-per-subtask", "3");
        System.setProperty("devflow.implementation.max-delivery-policy-files", "4");
        try {
            assertEquals(4, ImplementationExecutionPolicy.subtaskAttempts());
            assertEquals(5, ImplementationExecutionPolicy.planParseAttempts());
            assertEquals(2, ImplementationExecutionPolicy.internalPlanRetries());
            assertEquals(6, ImplementationExecutionPolicy.fileGenerationAttempts());
            assertEquals(3, ImplementationExecutionPolicy.maxFilesPerSubtask());
            assertEquals(4, ImplementationExecutionPolicy.maxDeliveryPolicyFiles());
        } finally {
            System.clearProperty("devflow.implementation.subtask-attempts");
            System.clearProperty("devflow.implementation.plan-parse-attempts");
            System.clearProperty("devflow.implementation.internal-plan-retries");
            System.clearProperty("devflow.implementation.file-generation-attempts");
            System.clearProperty("devflow.implementation.max-files-per-subtask");
            System.clearProperty("devflow.implementation.max-delivery-policy-files");
        }
    }
}
