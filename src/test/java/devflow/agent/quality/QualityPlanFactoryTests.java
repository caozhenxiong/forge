package devflow.agent.quality;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractRuntimeOwnershipMode;
import devflow.agent.context.ContractView;
import devflow.agent.context.EntryPackagingMode;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.executor.RuntimeSnapshot;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityPlanFactoryTests {

    @TempDir
    Path tempDir;

    @Test
    void factoryBuildsStructuredIntentWithoutGuessingPauseResetOrProgressFromSelectors() {
        ProjectFingerprint fingerprint = new ProjectFingerprint(
                "static-web",
                "none",
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                "index.html",
                Set.of("index.html"),
                List.of()
        );
        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(
                        true,
                        "html-entry",
                        EntryPackagingMode.ENTRY_WITH_LOCAL_DEPENDENCIES.wireValue(),
                        ContractRuntimeOwnershipMode.COMPANION_OWNED.wireValue(),
                        true,
                        true,
                        List.of("page-opens", "runtime-surface-renders")
                ).normalized(),
                ConstraintSourceMetadata.empty()
        );
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "Tetris",
                12,
                1,
                List.of("body", "#startBtn", "#pauseBtn", "#resetBtn", "#score-display", "button"),
                List.of(),
                List.of(),
                List.of()
        );
        ValidationMetadata metadata = new ValidationMetadata(true, 2000, 120);

        QualityPlan plan = new QualityPlanFactory().build(fingerprint, contractView, metadata, snapshot, List.of());

        assertTrue(plan.structureRiskReport().embeddedLogicRisk().atLeast(StructureRiskLevel.HIGH));
        assertFalse(plan.qualityIntent().structureIntent().preferLogicExternalization());
        assertFalse(plan.qualityIntent().structureIntent().requireStructureJustification());
        assertTrue(plan.capabilityMatrix().requires(CapabilityIds.PAGE_LOAD));
        assertTrue(plan.capabilityMatrix().requires(CapabilityIds.RUNTIME_STABILITY));
        assertTrue(plan.capabilityMatrix().requires(CapabilityIds.PRIMARY_VISUAL_SURFACE));
        assertTrue(plan.capabilityMatrix().requires(CapabilityIds.PRIMARY_INTERACTION));
        assertTrue(plan.capabilityMatrix().requires(CapabilityIds.PERFORMANCE_LOAD));
        assertTrue(plan.capabilityMatrix().requires(CapabilityIds.PERFORMANCE_INTERACTION));
        assertFalse(plan.capabilityMatrix().requires(CapabilityIds.PAUSE_FREEZE));
        assertFalse(plan.capabilityMatrix().requires(CapabilityIds.RESET_RESTORES_INITIAL_STATE));
        assertFalse(plan.capabilityMatrix().requires(CapabilityIds.VISIBLE_PROGRESS_SIGNAL));
    }

    @Test
    void emptyFactoryInputsProduceEmptyQualityPlan() {
        QualityPlan plan = new QualityPlanFactory().build(null, null, null, null, List.of());

        assertTrue(plan.capabilityMatrix().isEmpty());
        assertTrue(plan.qualityIntent().requiredCapabilityIds().isEmpty());
        assertFalse(plan.featureProfile().hasHtmlEntry());
        assertFalse(plan.featureProfile().hasDiscreteUserInput());
    }

    @Test
    void explicitRequiredCapabilitySurfacesFlowIntoQualityPlan() {
        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens")).normalized(),
                ConstraintSourceMetadata.empty()
        );

        QualityPlan plan = new QualityPlanFactory().build(
                null,
                contractView,
                ValidationMetadata.empty(),
                null,
                List.of(CapabilityIds.TIMED_STATE_PROGRESSION)
        );

        assertTrue(plan.qualityIntent().requiredCapabilityIds().contains(CapabilityIds.TIMED_STATE_PROGRESSION));
        assertTrue(plan.capabilityMatrix().requires(CapabilityIds.TIMED_STATE_PROGRESSION));
    }

    @Test
    void factoryLoadsProjectLevelQualityRulesWhenProjectPathIsPresent() throws Exception {
        Files.createDirectories(tempDir.resolve(".devflow"));
        Files.writeString(
                tempDir.resolve(".devflow").resolve("quality-rules.properties"),
                """
                structure.max-host-document-risk=HIGH
                verification.minimum-required-cases=5
                verification.required-capability-surfaces=visible-progress-signal
                """
        );

        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens")).normalized(),
                ConstraintSourceMetadata.empty()
        );

        QualityPlan plan = new QualityPlanFactory().build(
                tempDir,
                null,
                contractView,
                ValidationMetadata.empty(),
                null,
                List.of()
        );

        assertEquals(StructureRiskLevel.HIGH, plan.structurePolicy().maxHostDocumentRisk());
        assertEquals(5, plan.coveragePolicy().minimumRequiredCases());
        assertTrue(plan.qualityIntent().requiredCapabilityIds().contains(CapabilityIds.VISIBLE_PROGRESS_SIGNAL));
    }

    @Test
    void factoryPromotesStaticHtmlRuntimeSignalsWhenBrowserSnapshotIsMissing() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <body>
                  <canvas id="board"></canvas>
                  <button id="startBtn">开始</button>
                  <script>
                    console.log('inline runtime');
                  </script>
                </body>
                </html>
                """
        );

        ProjectFingerprint fingerprint = new ProjectFingerprint(
                "static-web",
                "none",
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                "index.html",
                Set.of("index.html"),
                List.of()
        );
        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(
                        true,
                        "html-entry",
                        EntryPackagingMode.ENTRY_WITH_LOCAL_DEPENDENCIES.wireValue(),
                        ContractRuntimeOwnershipMode.NOT_APPLICABLE.wireValue(),
                        true,
                        true,
                        List.of("page-opens", "runtime-surface-renders")
                ).normalized(),
                ConstraintSourceMetadata.empty()
        );

        QualityPlan plan = new QualityPlanFactory().build(
                tempDir,
                fingerprint,
                contractView,
                ValidationMetadata.empty(),
                null,
                List.of()
        );

        assertTrue(plan.featureProfile().hasCanvasSurface());
        assertTrue(plan.featureProfile().hasDiscreteUserInput());
        assertTrue(plan.structureRiskReport().embeddedLogicRisk().atLeast(StructureRiskLevel.HIGH));
        assertFalse(plan.structurePolicy().blockOnUnjustifiedEmbeddedDominance());
    }
}
