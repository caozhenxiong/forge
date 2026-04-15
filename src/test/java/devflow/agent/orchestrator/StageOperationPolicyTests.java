package devflow.agent.orchestrator;

import devflow.agent.domain.StageType;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StageOperationPolicyTests {

    private final StageOperationPolicy policy = new StageOperationPolicy();

    @Test
    void usesLongerGenerationTimeoutForExecutionStages() {
        assertEquals(Duration.ofMinutes(5), policy.generationTimeout(StageType.ANALYSIS));
        assertEquals(Duration.ofMinutes(5), policy.generationTimeout(StageType.DESIGN));
        assertEquals(Duration.ofMinutes(30), policy.generationTimeout(StageType.IMPLEMENTATION));
        assertEquals(Duration.ofMinutes(30), policy.generationTimeout(StageType.TEST));
    }

    @Test
    void usesLongerReviewTimeoutsForDocumentAndExecutionStages() {
        assertEquals(Duration.ofMinutes(3), policy.reviewTimeout(StageType.ANALYSIS));
        assertEquals(Duration.ofMinutes(3), policy.reviewTimeout(StageType.DESIGN));
        assertEquals(Duration.ofMinutes(5), policy.reviewTimeout(StageType.IMPLEMENTATION));
        assertEquals(Duration.ofMinutes(5), policy.reviewTimeout(StageType.TEST));
    }

    @Test
    void reviewTimeoutUsesConfiguredPolicyValues() {
        StageOperationPolicy configuredPolicy = new StageOperationPolicy(
                20,
                300,
                1_800,
                360,
                300
        );

        assertEquals(Duration.ofMinutes(6), configuredPolicy.reviewTimeout(StageType.DESIGN));
    }
}
