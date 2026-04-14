package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditUnitPlanningPolicyTests {

    @Test
    void valuesAreConfiguredThroughTypedPropertiesObject() {
        EditUnitPlanningPolicy policy = new EditUnitPlanningPolicy(3, 1, 0.55d);

        assertEquals(3, policy.maxSymbolsPerUnit());
        assertEquals(1, policy.inlineAppendSymbolBudget());
        assertEquals(0.55d, policy.maxPreFlightUnitRatio());
    }
}
