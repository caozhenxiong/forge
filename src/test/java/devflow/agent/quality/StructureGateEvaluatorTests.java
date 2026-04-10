package devflow.agent.quality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureGateEvaluatorTests {

    @Test
    void blocksEmbeddedDominanceFromRiskReportWithoutCapabilityHeuristic() {
        QualityPlan qualityPlan = new QualityPlan(
                new FeatureProfile(true, false, true, false, false, false, false, false, false),
                QualityIntent.empty(),
                new StructureRiskReport(
                        StructureRiskLevel.HIGH,
                        StructureRiskLevel.HIGH,
                        StructureRiskLevel.HIGH,
                        true,
                        true
                ),
                new StructurePolicy(true, true, StructureRiskLevel.MEDIUM),
                new CoveragePolicy(3, true, true),
                new ExperiencePolicy(false, false),
                CapabilityMatrix.empty(),
                QualityChecklist.empty()
        );

        StructureGateOutcome outcome = new StructureGateEvaluator().evaluate(null, qualityPlan);

        assertFalse(outcome.passed());
    }

    @Test
    void passesWhenEmbeddedLogicDoesNotExist() {
        QualityPlan qualityPlan = new QualityPlan(
                new FeatureProfile(true, true, false, true, true, true, true, true, false),
                QualityIntent.empty(),
                StructureRiskReport.low(),
                new StructurePolicy(true, true, StructureRiskLevel.MEDIUM),
                new CoveragePolicy(3, true, true),
                new ExperiencePolicy(false, false),
                CapabilityMatrix.empty(),
                QualityChecklist.empty()
        );

        StructureGateOutcome outcome = new StructureGateEvaluator().evaluate(null, qualityPlan);

        assertTrue(outcome.passed());
    }
}
