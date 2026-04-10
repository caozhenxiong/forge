package devflow.agent.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchRepairSettingsTests {

    @Test
    void syntaxRepairBudgetScalesWithCandidateSize() {
        PatchRepairSettings settings = new PatchRepairSettings();

        int smallBudget = settings.estimateSyntaxRepairNumPredict("function tick() { return 1; }");
        int largeBudget = settings.estimateSyntaxRepairNumPredict("x".repeat(4_000));

        assertTrue(smallBudget >= 160);
        assertTrue(largeBudget > smallBudget);
    }

    @Test
    void jsonRepairBudgetScalesWithPayloadSize() {
        PatchRepairSettings settings = new PatchRepairSettings();

        int smallBudget = settings.estimateJsonRepairNumPredict("{\"operations\":[]}");
        int largeBudget = settings.estimateJsonRepairNumPredict("{\"operations\":[\"" + "x".repeat(2_000) + "\"]}");

        assertTrue(smallBudget >= 160);
        assertTrue(largeBudget > smallBudget);
    }
}
