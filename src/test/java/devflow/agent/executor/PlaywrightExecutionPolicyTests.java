package devflow.agent.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaywrightExecutionPolicyTests {

    @Test
    void valuesCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.playwright.case-timeout-seconds", "45");
        System.setProperty("devflow.playwright.snapshot-timeout-seconds", "30");
        try {
            assertEquals(45, PlaywrightExecutionPolicy.caseExecutionTimeout().toSeconds());
            assertEquals(30, PlaywrightExecutionPolicy.snapshotTimeout().toSeconds());
        } finally {
            System.clearProperty("devflow.playwright.case-timeout-seconds");
            System.clearProperty("devflow.playwright.snapshot-timeout-seconds");
        }
    }
}
