package devflow.agent.executor.testing;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.CoverageLedgerEntry;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewRevisionRoute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 基于 contract、执行结果和覆盖账本解析 TEST 阶段失败 owner。
 */
final class ExperienceFailureDispositionResolver {

    private static final String MISSING_RUNTIME_ELEMENT_REASON = "missing-runtime-element";
    private static final String SETUP_FAILURE_REASON = "setup-failure";

    ExperienceFailureDisposition resolve(
            UiRuntimeContract contract,
            UiRuntimeContractValidation contractValidation,
            List<TestCaseSpec> plannedCases,
            List<TestCaseResult> caseResults,
            CoverageLedger coverageLedger
    ) {
        List<String> missingSurfaces = missingRequiredSurfaces(coverageLedger);
        if (contractValidation != null && !contractValidation.valid()) {
            if (contractValidation.kind() == UiRuntimeContractValidationKind.PROBE_INVALID) {
                return new ExperienceFailureDisposition(
                        ExperienceFailureKind.RUNTIME_PROBE_INVALID,
                        "测试阶段的浏览器 probe 无效，当前结果不能继续用于实现修复。",
                        "请先修复测试侧 probe/collector 失败，再重新执行当前测试阶段。",
                        contractValidation.evidence(),
                        ImplementationPatchTarget.NONE,
                        List.of(),
                        failedRequiredCaseIds(caseResults),
                        failureCapabilitySurfaces(plannedCases, caseResults, coverageLedger),
                        missingSurfaces,
                        ReviewRevisionRoute.REQUEST_HUMAN,
                        ReviewReasonCode.RUNTIME_PROBE_INVALID
                );
            }
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.OBSERVATION_CONTRACT_INVALID,
                    "测试阶段缺少可执行的运行时观测契约。",
                    "请修复真实运行时表面或交互后的可观察状态变化，再重新执行测试。",
                    contractValidation.evidence(),
                    implementationPatchTarget(contract),
                    ownerScopedOverrides(contract, "修复运行时表面与可观察状态变化"),
                    failedRequiredCaseIds(caseResults),
                    failureCapabilitySurfaces(plannedCases, caseResults, coverageLedger),
                    missingSurfaces,
                    implementationRepairRoute(contract),
                    ReviewReasonCode.OBSERVATION_CONTRACT_INVALID
            );
        }

        List<RequiredCasePlanDefect> planDefects = inspectPlanDefects(plannedCases);
        if (!planDefects.isEmpty()) {
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.TEST_PLAN_DEFECT,
                    "必测 testcase 结构与能力契约不一致，当前失败应先回到 TEST 计划修复。",
                    "请修复当前 TEST 阶段的必测用例骨架，确保 capability 对应的观测顺序和等待策略合法后再重新执行测试。",
                    planDefectEvidence(planDefects),
                    ImplementationPatchTarget.NONE,
                    List.of(),
                    planDefects.stream().map(RequiredCasePlanDefect::caseId).toList(),
                    planDefects.stream().map(RequiredCasePlanDefect::surfaceWireValue).distinct().toList(),
                    missingSurfaces,
                    ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                    ReviewReasonCode.TEST_PLAN_DEFECT
            );
        }

        if (coverageMissingWithoutCases(coverageLedger, plannedCases)) {
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.TEST_CASE_INCOMPLETE,
                    "测试阶段缺少必需能力项的可执行用例覆盖。",
                    "补齐缺失的必需能力测试用例并重新执行测试。",
                    "missingRequiredCoverageWithoutCases=" + String.join(", ", missingSurfaces),
                    ImplementationPatchTarget.NONE,
                    List.of(),
                    List.of(),
                    missingSurfaces,
                    missingSurfaces,
                    ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                    ReviewReasonCode.TEST_CASE_INCOMPLETE
            );
        }

        if (hasObservationContractFailure(caseResults)) {
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.OBSERVATION_CONTRACT_INVALID,
                    "测试阶段使用的观测目标与实际运行时不一致。",
                    "请先修复主观测面或交互后的真实可观察变化，再重新执行测试。",
                    firstObservationFailureEvidence(caseResults),
                    implementationPatchTarget(contract),
                    ownerScopedOverrides(contract, "修复观测主表面与交互后的状态变化"),
                    failedRequiredCaseIds(caseResults),
                    failureCapabilitySurfaces(plannedCases, caseResults, coverageLedger),
                    missingSurfaces,
                    implementationRepairRoute(contract),
                    ReviewReasonCode.OBSERVATION_CONTRACT_INVALID
            );
        }

        if (hasRequiredFailures(caseResults) || (coverageLedger != null && coverageLedger.hasMissingRequiredCoverage())) {
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                    "当前实现仍缺少关键体验能力的通过证据。",
                    "请在实现阶段补齐缺失能力，并重新执行测试。",
                    firstRequiredFailureEvidence(caseResults, coverageLedger),
                    implementationPatchTarget(contract),
                    ownerScopedOverrides(contract, "修复缺失体验能力与可观察反馈"),
                    failedRequiredCaseIds(caseResults),
                    failureCapabilitySurfaces(plannedCases, caseResults, coverageLedger),
                    missingSurfaces,
                    implementationRepairRoute(contract),
                    ReviewReasonCode.IMPLEMENTATION_GAP
            );
        }

        if (caseResults == null || caseResults.isEmpty()) {
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.UNKNOWN,
                    "测试阶段没有产出足够证据，无法继续自动推进。",
                    "请先检查测试执行与结构化产物，再决定下一步。",
                    "no-test-case-results",
                    ImplementationPatchTarget.NONE,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    ReviewRevisionRoute.REQUEST_HUMAN,
                    ReviewReasonCode.TEST_FAILURE_UNKNOWN
            );
        }

        return ExperienceFailureDisposition.pass();
    }

    private boolean coverageMissingWithoutCases(CoverageLedger coverageLedger, List<TestCaseSpec> plannedCases) {
        if (coverageLedger == null || !coverageLedger.hasMissingRequiredCoverage()) {
            return false;
        }
        LinkedHashSet<String> caseIds = new LinkedHashSet<>();
        if (plannedCases != null) {
            for (TestCaseSpec plannedCase : plannedCases) {
                if (plannedCase != null && plannedCase.id() != null && !plannedCase.id().isBlank()) {
                    caseIds.add(plannedCase.id().trim());
                }
            }
        }
        for (CoverageLedgerEntry entry : coverageLedger.entries()) {
            if (entry == null || !entry.missingRequired()) {
                continue;
            }
            if (entry.caseIds() == null || entry.caseIds().isEmpty()) {
                return true;
            }
            boolean anyPresent = entry.caseIds().stream().anyMatch(caseIds::contains);
            if (!anyPresent) {
                return true;
            }
        }
        return false;
    }

    private boolean hasObservationContractFailure(List<TestCaseResult> caseResults) {
        if (caseResults == null || caseResults.isEmpty()) {
            return false;
        }
        return caseResults.stream()
                .filter(result -> result != null && result.required())
                .anyMatch(result -> MISSING_RUNTIME_ELEMENT_REASON.equals(result.failureReason())
                        || SETUP_FAILURE_REASON.equals(result.failureReason()));
    }

    private String firstObservationFailureEvidence(List<TestCaseResult> caseResults) {
        if (caseResults == null) {
            return "";
        }
        return caseResults.stream()
                .filter(result -> result != null && result.required())
                .filter(result -> MISSING_RUNTIME_ELEMENT_REASON.equals(result.failureReason())
                        || SETUP_FAILURE_REASON.equals(result.failureReason()))
                .map(TestCaseResult::evidence)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private boolean hasRequiredFailures(List<TestCaseResult> caseResults) {
        if (caseResults == null || caseResults.isEmpty()) {
            return false;
        }
        return caseResults.stream()
                .filter(result -> result != null && result.required())
                .anyMatch(result -> result.status() == TestCaseStatus.FAILED || result.status() == TestCaseStatus.BLOCKED);
    }

    private String firstRequiredFailureEvidence(List<TestCaseResult> caseResults, CoverageLedger coverageLedger) {
        if (caseResults != null) {
            String evidence = caseResults.stream()
                    .filter(result -> result != null && result.required())
                    .filter(result -> result.status() == TestCaseStatus.FAILED || result.status() == TestCaseStatus.BLOCKED)
                    .map(TestCaseResult::evidence)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("");
            if (!evidence.isBlank()) {
                return evidence;
            }
        }
        List<String> missing = missingRequiredSurfaces(coverageLedger);
        return missing.isEmpty() ? "" : "missingExperienceCoverage=" + String.join(", ", missing);
    }

    private List<String> failedRequiredCaseIds(List<TestCaseResult> caseResults) {
        if (caseResults == null || caseResults.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> caseIds = new LinkedHashSet<>();
        for (TestCaseResult result : caseResults) {
            if (result == null || !result.required()) {
                continue;
            }
            if ((result.status() == TestCaseStatus.FAILED || result.status() == TestCaseStatus.BLOCKED)
                    && result.id() != null
                    && !result.id().isBlank()) {
                caseIds.add(result.id().trim());
            }
        }
        return List.copyOf(caseIds);
    }

    private List<String> failureCapabilitySurfaces(
            List<TestCaseSpec> plannedCases,
            List<TestCaseResult> caseResults,
            CoverageLedger coverageLedger
    ) {
        LinkedHashSet<String> surfaces = new LinkedHashSet<>(missingRequiredSurfaces(coverageLedger));
        if (plannedCases == null || plannedCases.isEmpty() || caseResults == null || caseResults.isEmpty()) {
            return List.copyOf(surfaces);
        }
        LinkedHashSet<String> failedIds = new LinkedHashSet<>(failedRequiredCaseIds(caseResults));
        if (failedIds.isEmpty()) {
            return List.copyOf(surfaces);
        }
        for (TestCaseSpec plannedCase : plannedCases) {
            if (plannedCase == null || plannedCase.id() == null || !failedIds.contains(plannedCase.id().trim())) {
                continue;
            }
            for (String capabilityId : plannedCase.capabilities()) {
                if (capabilityId != null && !capabilityId.isBlank()) {
                    surfaces.add(capabilityId);
                }
            }
        }
        return List.copyOf(surfaces);
    }

    private List<String> missingRequiredSurfaces(CoverageLedger coverageLedger) {
        if (coverageLedger == null || coverageLedger.entries() == null || coverageLedger.entries().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> surfaces = new LinkedHashSet<>();
        for (CoverageLedgerEntry entry : coverageLedger.entries()) {
            if (entry == null || !entry.missingRequired() || entry.capabilityId().isBlank()) {
                continue;
            }
            surfaces.add(entry.capabilityId());
        }
        return List.copyOf(surfaces);
    }

    private List<FileChange> ownerScopedOverrides(UiRuntimeContract contract, String reason) {
        if (contract == null || contract.ownerPaths().isEmpty()) {
            return List.of();
        }
        List<FileChange> overrides = new ArrayList<>();
        for (String ownerPath : contract.ownerPaths()) {
            if (ownerPath == null || ownerPath.isBlank()) {
                continue;
            }
            overrides.add(new FileChange(ownerPath, ChangeAction.WRITE, reason));
        }
        return List.copyOf(overrides);
    }

    private ImplementationPatchTarget implementationPatchTarget(UiRuntimeContract contract) {
        return hasImplementationOwnerScope(contract)
                ? ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                : ImplementationPatchTarget.NONE;
    }

    private ReviewRevisionRoute implementationRepairRoute(UiRuntimeContract contract) {
        return hasImplementationOwnerScope(contract)
                ? ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET
                : ReviewRevisionRoute.REQUEST_HUMAN;
    }

    private boolean hasImplementationOwnerScope(UiRuntimeContract contract) {
        if (contract == null || contract.ownerPaths().isEmpty()) {
            return false;
        }
        for (String ownerPath : contract.ownerPaths()) {
            if (ownerPath != null && !ownerPath.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private List<RequiredCasePlanDefect> inspectPlanDefects(List<TestCaseSpec> plannedCases) {
        if (plannedCases == null || plannedCases.isEmpty()) {
            return List.of();
        }
        List<RequiredCasePlanDefect> defects = new ArrayList<>();
        for (TestCaseSpec plannedCase : plannedCases) {
            if (plannedCase == null || !plannedCase.required() || plannedCase.steps().isEmpty()) {
                continue;
            }
            RequiredCasePlanDefect defect = inspectObservationPlanDefect(plannedCase);
            if (defect != null) {
                defects.add(defect);
            }
        }
        return List.copyOf(defects);
    }

    private RequiredCasePlanDefect inspectObservationPlanDefect(TestCaseSpec testCase) {
        if (testCase == null || !testCase.requiresObservationWindow()) {
            return null;
        }
        return testCase.observationTrigger() == TestObservationTrigger.AFTER_WAIT
                ? inspectDelayedObservationDefect(testCase)
                : inspectInteractiveObservationDefect(testCase);
    }

    private RequiredCasePlanDefect inspectDelayedObservationDefect(TestCaseSpec testCase) {
        int assertIndex = firstActionIndex(
                testCase.steps(),
                TestStepAction.ASSERT_CANVAS_HASH_CHANGED,
                TestStepAction.ASSERT_CANVAS_HASH_UNCHANGED,
                TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED,
                TestStepAction.ASSERT_DOM_SIGNATURE_UNCHANGED
        );
        if (assertIndex < 0) {
            return null;
        }
        int snapshotIndex = firstActionIndex(testCase.steps(), TestStepAction.SNAPSHOT_CANVAS_HASH, TestStepAction.SNAPSHOT_DOM_SIGNATURE);
        int waitIndex = firstActionIndex(testCase.steps(), TestStepAction.WAIT);
        if (snapshotIndex < 0 || waitIndex < 0 || snapshotIndex > waitIndex || waitIndex > assertIndex) {
            return new RequiredCasePlanDefect(
                    safeCaseId(testCase),
                    defectCapabilityId(testCase),
                    "delayed observation must be snapshot -> WAIT(policy) -> ASSERT_COMPARE"
            );
        }
        int setupInteractionIndex = previousInteractiveIndex(testCase.steps(), waitIndex + 1);
        if (setupInteractionIndex >= 0 && snapshotIndex < setupInteractionIndex) {
            return new RequiredCasePlanDefect(
                    safeCaseId(testCase),
                    defectCapabilityId(testCase),
                    "delayed observation baseline must be captured after run-state setup and before WAIT(policy)"
            );
        }
        TestStepSpec waitStep = testCase.steps().get(waitIndex);
        int expectedWait = TestPlanningPolicy.observationWaitMs(testCase.observationTrigger());
        if (waitStep.ms() == null || waitStep.ms() != expectedWait) {
            return new RequiredCasePlanDefect(
                    safeCaseId(testCase),
                    defectCapabilityId(testCase),
                    "delayed observation wait must equal policy value " + expectedWait + "ms"
            );
        }
        return null;
    }

    private RequiredCasePlanDefect inspectInteractiveObservationDefect(TestCaseSpec testCase) {
        int assertIndex = firstActionIndex(
                testCase.steps(),
                TestStepAction.ASSERT_CANVAS_HASH_CHANGED,
                TestStepAction.ASSERT_CANVAS_HASH_UNCHANGED,
                TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED,
                TestStepAction.ASSERT_DOM_SIGNATURE_UNCHANGED
        );
        if (assertIndex < 0) {
            return null;
        }
        int interactiveIndex = previousInteractiveIndex(testCase.steps(), assertIndex);
        if (interactiveIndex < 0) {
            return null;
        }
        int snapshotIndex = previousActionIndex(
                testCase.steps(),
                interactiveIndex,
                TestStepAction.SNAPSHOT_CANVAS_HASH,
                TestStepAction.SNAPSHOT_DOM_SIGNATURE
        );
        if (snapshotIndex < 0) {
            return new RequiredCasePlanDefect(
                    safeCaseId(testCase),
                    defectCapabilityId(testCase),
                    "interactive observation must capture baseline snapshot before the interaction"
            );
        }
        int waitIndex = nextActionIndex(testCase.steps(), interactiveIndex + 1, assertIndex, TestStepAction.WAIT);
        if (waitIndex < 0) {
            return new RequiredCasePlanDefect(
                    safeCaseId(testCase),
                    defectCapabilityId(testCase),
                    "interactive observation must wait between interaction and compare assertion"
            );
        }
        TestStepSpec waitStep = testCase.steps().get(waitIndex);
        int expectedWait = TestPlanningPolicy.observationWaitMs(testCase.observationTrigger());
        if (waitStep.ms() == null || waitStep.ms() != expectedWait) {
            return new RequiredCasePlanDefect(
                    safeCaseId(testCase),
                    defectCapabilityId(testCase),
                    "interactive observation wait must equal policy value " + expectedWait + "ms"
            );
        }
        return null;
    }

    private int firstActionIndex(List<TestStepSpec> steps, TestStepAction... actions) {
        if (steps == null || steps.isEmpty() || actions == null || actions.length == 0) {
            return -1;
        }
        for (int index = 0; index < steps.size(); index++) {
            TestStepAction current = steps.get(index).action();
            for (TestStepAction action : actions) {
                if (current == action) {
                    return index;
                }
            }
        }
        return -1;
    }

    private int previousInteractiveIndex(List<TestStepSpec> steps, int beforeIndex) {
        for (int index = beforeIndex - 1; index >= 0; index--) {
            TestStepAction action = steps.get(index).action();
            if (action != null && action.isInteractive()) {
                return index;
            }
        }
        return -1;
    }

    private int previousActionIndex(List<TestStepSpec> steps, int beforeIndex, TestStepAction... actions) {
        for (int index = beforeIndex - 1; index >= 0; index--) {
            TestStepAction current = steps.get(index).action();
            for (TestStepAction action : actions) {
                if (current == action) {
                    return index;
                }
            }
        }
        return -1;
    }

    private int nextActionIndex(List<TestStepSpec> steps, int startIndex, int endExclusive, TestStepAction action) {
        for (int index = Math.max(0, startIndex); index < Math.min(steps.size(), endExclusive); index++) {
            if (steps.get(index).action() == action) {
                return index;
            }
        }
        return -1;
    }

    private String planDefectEvidence(List<RequiredCasePlanDefect> defects) {
        return defects.stream()
                .map(defect -> defect.caseId() + " | " + defect.surfaceWireValue() + " | " + defect.reason())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String safeCaseId(TestCaseSpec testCase) {
        return testCase.id() == null || testCase.id().isBlank() ? "unknown-case" : testCase.id().trim();
    }

    private String defectCapabilityId(TestCaseSpec testCase) {
        if (testCase == null) {
            return CapabilityIds.PRIMARY_INTERACTION;
        }
        if (testCase.observationTargetId() != null && !testCase.observationTargetId().isBlank()) {
            return testCase.observationTargetId().trim();
        }
        if (testCase.capabilities() != null && !testCase.capabilities().isEmpty()) {
            return testCase.capabilities().getFirst();
        }
        return CapabilityIds.PRIMARY_INTERACTION;
    }

    private record RequiredCasePlanDefect(String caseId, String surfaceWireValue, String reason) {
    }
}
