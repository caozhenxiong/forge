package devflow.agent.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchBudgetSettingsTests {

    @Test
    void defaultsAllowSystemPropertyOverrides() {
        String unitPerTargetKey = "devflow.patch.unit-budget-ratio-per-target";
        String maxPreFlightKey = "devflow.patch.max-preflight-unit-ratio";
        String previousUnitPerTarget = System.getProperty(unitPerTargetKey);
        String previousMaxPreFlight = System.getProperty(maxPreFlightKey);
        try {
            System.setProperty(unitPerTargetKey, "0.3");
            System.setProperty(maxPreFlightKey, "0.8");

            PatchBudgetSettings settings = PatchBudgetSettings.defaults();

            assertEquals(0.3d, settings.unitBudgetRatioPerTarget());
            assertEquals(0.8d, settings.maxPreFlightUnitRatio());
        } finally {
            restoreProperty(unitPerTargetKey, previousUnitPerTarget);
            restoreProperty(maxPreFlightKey, previousMaxPreFlight);
        }
    }

    private void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, previousValue);
    }
}
