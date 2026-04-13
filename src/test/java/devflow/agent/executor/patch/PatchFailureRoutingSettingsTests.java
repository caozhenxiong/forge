package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchFailureRoutingSettingsTests {

    @Test
    void defaultsAllowSystemPropertyOverrides() {
        String key = "devflow.patch.unsplittable-max-attempts";
        String previousValue = System.getProperty(key);
        try {
            System.setProperty(key, "4");

            PatchFailureRoutingSettings settings = PatchFailureRoutingSettings.defaults();

            assertEquals(4, settings.unsplittableUnitMaxAttempts());
        } finally {
            if (previousValue == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previousValue);
            }
        }
    }
}
