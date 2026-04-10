package devflow.agent.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StageOperationPolicyTests {

    private final StageOperationPolicy policy = new StageOperationPolicy();

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("devflow.stage.document-review-timeout-seconds");
    }

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
    void reviewTimeoutCanBeOverriddenFromSystemProperty() {
        System.setProperty("devflow.stage.document-review-timeout-seconds", "360");

        assertEquals(Duration.ofMinutes(6), policy.reviewTimeout(StageType.DESIGN));
    }
}
