package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.quality.CapabilityExpectation;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.CoverageLedgerStatus;
import devflow.agent.quality.CoveragePolicy;
import devflow.agent.quality.ExperiencePolicy;
import devflow.agent.quality.FeatureProfile;
import devflow.agent.quality.QualityChecklist;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityIntent;
import devflow.agent.quality.StructurePolicy;
import devflow.agent.quality.StructureRiskLevel;
import devflow.agent.quality.StructureRiskReport;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverageLedgerBuilderTests {

    @Test
    void buildsCoverageStatusFromPlannedCasesAndExecutionResults() {
        QualityPlan qualityPlan = new QualityPlan(
                new FeatureProfile(true, true, false, true, false, false, false, false, false),
                QualityIntent.empty(),
                StructureRiskReport.low(),
                new StructurePolicy(true, true, StructureRiskLevel.MEDIUM),
                new CoveragePolicy(3, true, true),
                new ExperiencePolicy(true, true),
                new CapabilityMatrix(List.of(
                        new CapabilityMatrixEntry(CapabilityIds.PAGE_LOAD, CapabilityExpectation.REQUIRED, false, ""),
                        new CapabilityMatrixEntry(CapabilityIds.PRIMARY_INTERACTION, CapabilityExpectation.REQUIRED, true, "")
                )),
                QualityChecklist.empty()
        );
        TestCasePlan plan = new TestCasePlan(
                "summary",
                List.of(
                        new TestCaseSpec("TC-LOAD", "load", "smoke", true, "index.html", "", "", List.of(), List.of(CapabilityIds.PAGE_LOAD)),
                        new TestCaseSpec("TC-INTERACT", "interact", "functional", true, "index.html", "", "", List.of(), List.of(CapabilityIds.PRIMARY_INTERACTION))
                ),
                qualityPlan
        );

        CoverageLedger ledger = new CoverageLedgerBuilder().build(
                plan,
                List.of(
                        new TestCaseResult("TC-LOAD", "load", TestCaseStatus.PASSED, true, "", "", ""),
                        new TestCaseResult("TC-INTERACT", "interact", TestCaseStatus.FAILED, true, "", "failure", "")
                )
        );

        assertEquals(CoverageLedgerStatus.COVERED, ledger.entries().get(0).status());
        assertEquals(CoverageLedgerStatus.FAILED, ledger.entries().get(1).status());
    }
}
