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

import devflow.agent.executor.subtask.SubtaskReviewPolicy;
class SubtaskReviewPolicyTests {

    @Test
    void valuesAreConfiguredThroughTypedPropertiesObject() {
        SubtaskReviewPolicy policy = new SubtaskReviewPolicy(3, 15, 75);

        assertEquals(3, policy.maxAttempts());
        assertEquals(15, policy.heartbeatInterval().toSeconds());
        assertEquals(75, policy.attemptTimeout().toSeconds());
    }
}
