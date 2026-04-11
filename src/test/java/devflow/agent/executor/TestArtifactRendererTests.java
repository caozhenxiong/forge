package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.quality.CapabilityExpectation;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.quality.CapabilitySurface;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.CoverageLedgerEntry;
import devflow.agent.quality.CoverageLedgerStatus;
import devflow.agent.quality.CoveragePolicy;
import devflow.agent.quality.ExperiencePolicy;
import devflow.agent.quality.FeatureProfile;
import devflow.agent.quality.QualityChecklist;
import devflow.agent.quality.QualityLedger;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityIntent;
import devflow.agent.quality.StructurePolicy;
import devflow.agent.quality.StructureRiskLevel;
import devflow.agent.quality.StructureRiskReport;
import devflow.agent.review.ImplementationPatchTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestArtifactRendererTests {

    @Test
    void renderExecutionIncludesStructuredToolResultsBlock() {
        TestArtifactRenderer renderer = new TestArtifactRenderer();
        String markdown = renderer.renderExecution(
                new CollectedTestEvidence(
                        new SelfCheckResult(true, "ok", "details"),
                        ArchitectIntegrationCheckResult.success(),
                        null,
                        List.of(),
                        List.of(ToolResult.failure(
                                ToolName.TEST_CASE_EXECUTION,
                                ToolFailureCode.TEST_CASE_EXECUTION_FAILED,
                                "execution failed",
                                "rerun tests"
                        ))
                ),
                UiRuntimeContract.empty(),
                ExperienceFailureDisposition.pass(),
                DocumentLanguage.EN
        );

        assertTrue(markdown.contains(ArtifactBlockKind.TOOL_RESULTS.beginMarker()), markdown);
        assertTrue(markdown.contains(ArtifactBlockKind.QUALITY_LEDGER.beginMarker()), markdown);
        assertTrue(markdown.contains("TEST_CASE_EXECUTION_FAILED"), markdown);
    }

    private CollectedTestEvidence evidenceWithQualityLedger() {
        QualityPlan qualityPlan = new QualityPlan(
                new FeatureProfile(true, false, true, true, false, false, false, true, false),
                QualityIntent.empty(),
                new StructureRiskReport(StructureRiskLevel.HIGH, StructureRiskLevel.HIGH, StructureRiskLevel.HIGH, true, true),
                new StructurePolicy(true, true, StructureRiskLevel.MEDIUM),
                new CoveragePolicy(3, true, true),
                new ExperiencePolicy(true, true),
                new CapabilityMatrix(List.of(
                        new CapabilityMatrixEntry(CapabilitySurface.PAGE_LOAD, CapabilityExpectation.REQUIRED, false, "entry must load")
                )),
                QualityChecklist.empty()
        );
        QualityLedger qualityLedger = new QualityLedger(
                qualityPlan.structureRiskReport(),
                qualityPlan.capabilityMatrix(),
                new CoverageLedger(List.of(
                        new CoverageLedgerEntry(CapabilitySurface.PAGE_LOAD, true, CoverageLedgerStatus.MISSING, List.of(), "missing")
                ))
        );
        return new CollectedTestEvidence(
                new SelfCheckResult(true, "ok", "details"),
                ArchitectIntegrationCheckResult.success(),
                null,
                List.of(),
                List.of(ToolResult.failure(
                        ToolName.TEST_CASE_EXECUTION,
                        ToolFailureCode.TEST_CASE_EXECUTION_FAILED,
                        "execution failed",
                        "rerun tests"
                )),
                qualityLedger.coverageLedger(),
                qualityLedger
        );
    }
}
