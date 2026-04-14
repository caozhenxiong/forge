package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchBudgetSettingsTests {

    @Test
    void valuesAreConfiguredThroughTypedPropertiesObject() {
        PatchBudgetSettings settings = new PatchBudgetSettings(0.3d, 0.8d);

        assertEquals(0.3d, settings.unitBudgetRatioPerTarget());
        assertEquals(0.8d, settings.maxPreFlightUnitRatio());
    }
}
