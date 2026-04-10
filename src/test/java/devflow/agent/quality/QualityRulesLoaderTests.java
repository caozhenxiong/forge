package devflow.agent.quality;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityRulesLoaderTests {

    @TempDir
    Path tempDir;

    @Test
    void missingResourceDefaultsFailFast() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new QualityRulesLoader("/missing-quality-rules.properties").loadDefaults()
        );

        assertTrue(exception.getMessage().contains("Missing quality rules resource"));
    }

    @Test
    void projectRuleFileOverridesResourceDefaults() throws Exception {
        Path rulesDir = tempDir.resolve(".devflow");
        Files.createDirectories(rulesDir);
        Files.writeString(
                rulesDir.resolve("quality-rules.properties"),
                """
                structure.prefer-logic-externalization=false
                verification.minimum-required-cases=6
                verification.required-capability-surfaces=pause-freeze,visible-progress-signal
                experience.promote-timed-progression-coverage-from-feature-profile=false
                """
        );

        QualityRules rules = new QualityRulesLoader().load(tempDir);

        assertFalse(rules.structureRules().preferLogicExternalization());
        assertEquals(6, rules.verificationRules().minimumRequiredCases());
        assertFalse(rules.experienceRules().promoteTimedProgressionCoverageFromFeatureProfile());
        assertTrue(rules.verificationRules().requiredCapabilitySurfaces().contains(CapabilitySurface.PAUSE_FREEZE));
        assertTrue(rules.verificationRules().requiredCapabilitySurfaces().contains(CapabilitySurface.VISIBLE_PROGRESS_SIGNAL));
    }

    @Test
    void resourceDefaultsRemainAvailableWithoutProjectRuleFile() {
        QualityRules rules = new QualityRulesLoader().load(tempDir);

        assertTrue(rules.structureRules().preferLogicExternalization());
        assertEquals(3, rules.verificationRules().minimumRequiredCases());
        assertTrue(rules.experienceRules().gateOnMissingRequiredExperienceCoverage());
        assertTrue(rules.experienceRules().promoteTimedProgressionCoverageFromFeatureProfile());
    }

    @Test
    void invalidProjectRuleFileFailsFast() throws Exception {
        Path rulesDir = tempDir.resolve(".devflow");
        Files.createDirectories(rulesDir);
        Files.writeString(
                rulesDir.resolve("quality-rules.properties"),
                """
                structure.prefer-logic-externalization=maybe
                """
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new QualityRulesLoader().load(tempDir)
        );

        assertTrue(exception.getMessage().contains("Invalid boolean quality rule"));
    }
}
