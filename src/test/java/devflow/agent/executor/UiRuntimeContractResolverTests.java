package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.testing.*;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CapabilityExpectation;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiRuntimeContractResolverTests {

    @TempDir
    Path tempDir;

    @Test
    void validateMarksProbeFailureAsProbeInvalid() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<!doctype html><html><body><main id='app'></main></body></html>");

        UiRuntimeContractResolver resolver = new UiRuntimeContractResolver(new FileProjectWorkspace(), new TreeSitterSupport());
        UiRuntimeContractValidation validation = resolver.validate(
                tempDir,
                QualityPlan.empty(),
                new RuntimeSnapshot(
                        "index.html",
                        "",
                        null,
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        RuntimeSnapshotCaptureStatus.COLLECTOR_FAILED,
                        RuntimeSnapshotFailureCode.COLLECTOR_EXECUTION_FAILED,
                        List.of("probe crashed")
                ),
                new UiRuntimeContract("index.html", List.of("index.html"), List.of(), List.of())
        );

        assertFalse(validation.valid());
        assertEquals(UiRuntimeContractValidationKind.PROBE_INVALID, validation.kind());
        assertTrue(validation.issues().contains("probe crashed"));
    }

    @Test
    void resolveUsesCanvasForInteractiveObservationWhileKeepingVisualSurfaceIndependent() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<!doctype html><html><body><canvas id='game'></canvas></body></html>");

        UiRuntimeContractResolver resolver = new UiRuntimeContractResolver(new FileProjectWorkspace(), new TreeSitterSupport());
        UiRuntimeContract contract = resolver.resolve(
                tempDir,
                fingerprint("index.html"),
                qualityPlan(
                        CapabilityIds.PRIMARY_VISUAL_SURFACE,
                        CapabilityIds.PRIMARY_INTERACTION,
                        CapabilityIds.TIMED_STATE_PROGRESSION
                ),
                new RuntimeSnapshot(
                        "index.html",
                        "Canvas app",
                        10,
                        1,
                        List.of("body", "#game"),
                        List.of(
                                new RuntimeSurfaceCandidate("body", UiObservationMode.DOM_SIGNATURE, 1000),
                                new RuntimeSurfaceCandidate("#game", UiObservationMode.CANVAS_HASH, 600)
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        UiObservationTarget visual = contract.targetFor(CapabilityIds.PRIMARY_VISUAL_SURFACE);
        UiObservationTarget interaction = contract.targetFor(CapabilityIds.PRIMARY_INTERACTION);

        assertEquals("body", visual.selector());
        assertEquals(UiObservationMode.DOM_SIGNATURE, visual.mode());
        assertEquals("#game", interaction.selector());
        assertEquals(UiObservationMode.CANVAS_HASH, interaction.mode());
        assertEquals(2, contract.observationTargets().size());
    }

    @Test
    void enrichRunStateEntryTargetsKeepsOnlySelectorsBackedByRuntimeControls() {
        UiRuntimeContractResolver resolver = new UiRuntimeContractResolver(new FileProjectWorkspace(), new TreeSitterSupport());
        UiRuntimeContract contract = new UiRuntimeContract(
                "index.html",
                List.of("index.html"),
                List.of("body", "#start-btn"),
                List.of()
        );
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                "index.html",
                "App",
                12,
                0,
                List.of("body", "#start-btn"),
                List.of(),
                List.of(new RuntimeControlCandidate("#start-btn", "Start")),
                List.of(),
                List.of(),
                List.of()
        );
        List<TestCaseSpec> cases = List.of(new TestCaseSpec(
                "TC-001",
                "start",
                "functional",
                true,
                "index.html",
                "",
                "",
                List.of(
                        new TestStepSpec(TestStepAction.ASSERT_SELECTOR, "body", null, null, null, null, false, TestStepSemantic.RUN_STATE_ENTRY),
                        new TestStepSpec(TestStepAction.CLICK, "#start-btn", null, null, null, null, false, TestStepSemantic.RUN_STATE_ENTRY)
                )
        ));

        UiRuntimeContract enriched = resolver.enrichRunStateEntryTargets(contract, snapshot, cases);

        assertEquals(List.of("#start-btn"), enriched.runStateEntryTargets());
    }

    private ProjectFingerprint fingerprint(String entryPath) {
        return new ProjectFingerprint(
                "web",
                "",
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                entryPath,
                Set.of(entryPath),
                List.of()
        );
    }

    private QualityPlan qualityPlan(String... capabilityIds) {
        QualityPlan base = QualityPlan.empty();
        List<CapabilityMatrixEntry> entries = java.util.Arrays.stream(capabilityIds)
                .map(capabilityId -> new CapabilityMatrixEntry(
                        capabilityId,
                        CapabilityExpectation.REQUIRED,
                        CapabilityIds.PRIMARY_INTERACTION.equals(capabilityId),
                        ""
                ))
                .toList();
        return new QualityPlan(
                base.featureProfile(),
                base.qualityIntent(),
                base.structureRiskReport(),
                base.structurePolicy(),
                base.coveragePolicy(),
                base.experiencePolicy(),
                new CapabilityMatrix(entries),
                base.qualityChecklist()
        );
    }
}
