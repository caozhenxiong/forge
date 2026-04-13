package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import devflow.agent.executor.subtask.SubtaskReviewPolicy;
class SubtaskReviewPolicyTests {

    @Test
    void valuesCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.subtask-review.max-attempts", "3");
        System.setProperty("devflow.subtask-review.heartbeat-seconds", "15");
        System.setProperty("devflow.subtask-review.attempt-timeout-seconds", "75");
        try {
            assertEquals(3, SubtaskReviewPolicy.maxAttempts());
            assertEquals(15, SubtaskReviewPolicy.heartbeatInterval().toSeconds());
            assertEquals(75, SubtaskReviewPolicy.attemptTimeout().toSeconds());
        } finally {
            System.clearProperty("devflow.subtask-review.max-attempts");
            System.clearProperty("devflow.subtask-review.heartbeat-seconds");
            System.clearProperty("devflow.subtask-review.attempt-timeout-seconds");
        }
    }
}
