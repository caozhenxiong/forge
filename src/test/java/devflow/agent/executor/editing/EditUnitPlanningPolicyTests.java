package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditUnitPlanningPolicyTests {

    @Test
    void valuesCanBeOverriddenBySystemProperties() {
        System.setProperty("devflow.edit-unit.max-symbols-per-unit", "3");
        System.setProperty("devflow.edit-unit.inline-append-symbol-budget", "1");
        System.setProperty("devflow.edit-unit.max-preflight-unit-ratio", "0.55");
        try {
            assertEquals(3, EditUnitPlanningPolicy.maxSymbolsPerUnit());
            assertEquals(1, EditUnitPlanningPolicy.inlineAppendSymbolBudget());
            assertEquals(0.55d, EditUnitPlanningPolicy.maxPreFlightUnitRatio());
        } finally {
            System.clearProperty("devflow.edit-unit.max-symbols-per-unit");
            System.clearProperty("devflow.edit-unit.inline-append-symbol-budget");
            System.clearProperty("devflow.edit-unit.max-preflight-unit-ratio");
        }
    }
}
