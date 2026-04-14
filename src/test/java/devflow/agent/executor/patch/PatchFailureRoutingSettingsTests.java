package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchFailureRoutingSettingsTests {

    @Test
    void valuesAreConfiguredThroughTypedPropertiesObject() {
        PatchFailureRoutingSettings settings = new PatchFailureRoutingSettings(4);

        assertEquals(4, settings.unsplittableUnitMaxAttempts());
    }
}
