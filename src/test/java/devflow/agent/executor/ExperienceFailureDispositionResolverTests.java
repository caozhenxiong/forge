package devflow.agent.executor;

import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.quality.CapabilityIds;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceFailureDispositionResolverTests {

    private final ExperienceFailureDispositionResolver resolver = new ExperienceFailureDispositionResolver();

    @Test
    void probeInvalidDoesNotRouteBackToImplementation() {
        ExperienceFailureDisposition disposition = resolver.resolve(
                UiRuntimeContract.empty(),
                UiRuntimeContractValidation.failure(
                        UiRuntimeContractValidationKind.PROBE_INVALID,
                        List.of("probe crashed")
                ),
                List.of(),
                List.of(),
                devflow.agent.quality.CoverageLedger.empty()
        );

        assertEquals(ExperienceFailureKind.RUNTIME_PROBE_INVALID, disposition.kind());
        assertEquals(ImplementationPatchTarget.NONE, disposition.implementationPatchTarget());
        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, disposition.revisionRoute());
        assertEquals(ReviewReasonCode.RUNTIME_PROBE_INVALID, disposition.reasonCode());
        assertTrue(disposition.changeRequest().contains("probe"));
    }

    @Test
    void missingObservationTargetsStillRouteToImplementationRepair() {
        ExperienceFailureDisposition disposition = resolver.resolve(
                new UiRuntimeContract("index.html", List.of("index.html"), List.of("#start"), List.of()),
                UiRuntimeContractValidation.failure(
                        UiRuntimeContractValidationKind.MISSING_REQUIRED_OBSERVATION_TARGET,
                        List.of("missing target")
                ),
                List.of(),
                List.of(),
                devflow.agent.quality.CoverageLedger.empty()
        );

        assertEquals(ExperienceFailureKind.OBSERVATION_CONTRACT_INVALID, disposition.kind());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, disposition.implementationPatchTarget());
        assertEquals(ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET, disposition.revisionRoute());
        assertEquals(ReviewReasonCode.OBSERVATION_CONTRACT_INVALID, disposition.reasonCode());
    }

    @Test
    void missingObservationTargetsWithoutOwnerScopeBlockForHuman() {
        ExperienceFailureDisposition disposition = resolver.resolve(
                UiRuntimeContract.empty(),
                UiRuntimeContractValidation.failure(
                        UiRuntimeContractValidationKind.MISSING_REQUIRED_OBSERVATION_TARGET,
                        List.of("missing target")
                ),
                List.of(),
                List.of(),
                devflow.agent.quality.CoverageLedger.empty()
        );

        assertEquals(ExperienceFailureKind.OBSERVATION_CONTRACT_INVALID, disposition.kind());
        assertEquals(ImplementationPatchTarget.NONE, disposition.implementationPatchTarget());
        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, disposition.revisionRoute());
        assertTrue(disposition.overrideChanges().isEmpty());
    }

    @Test
    void malformedTimedProgressionCaseRoutesToTestPlanDefect() {
        ExperienceFailureDisposition disposition = resolver.resolve(
                UiRuntimeContract.empty(),
                UiRuntimeContractValidation.success(),
                List.of(new TestCaseSpec(
                        "TC-005",
                        "timed progression",
                        "functional",
                        true,
                        "index.html",
                        "",
                        "",
                        List.of(
                                new TestStepSpec(TestStepAction.WAIT, null, null, null, 1000, null, false),
                                new TestStepSpec(TestStepAction.SNAPSHOT_CANVAS_HASH, "#board", null, null, null, "timed", false, TestStepSemantic.PRIMARY_SURFACE),
                                new TestStepSpec(TestStepAction.ASSERT_CANVAS_HASH_CHANGED, "#board", null, null, null, "timed", false, TestStepSemantic.PRIMARY_SURFACE)
                        ),
                        List.of(CapabilityIds.TIMED_STATE_PROGRESSION),
                        CapabilityIds.PRIMARY_INTERACTION,
                        TestObservationTrigger.AFTER_WAIT,
                        TestObservationComparison.CHANGED
                )),
                List.of(),
                devflow.agent.quality.CoverageLedger.empty()
        );

        assertEquals(ExperienceFailureKind.TEST_PLAN_DEFECT, disposition.kind());
        assertEquals(ImplementationPatchTarget.NONE, disposition.implementationPatchTarget());
        assertEquals(ReviewRevisionRoute.PATCH_CURRENT_STAGE, disposition.revisionRoute());
        assertEquals(ReviewReasonCode.TEST_PLAN_DEFECT, disposition.reasonCode());
        assertEquals(List.of("TC-005"), disposition.failingCaseIds());
        assertEquals(List.of(CapabilityIds.PRIMARY_INTERACTION), disposition.failureCapabilitySurfaces());
        assertTrue(disposition.evidence().contains("snapshot -> WAIT(policy) -> ASSERT_COMPARE"));
    }
}
