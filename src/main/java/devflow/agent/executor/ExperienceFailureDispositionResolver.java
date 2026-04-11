package devflow.agent.executor;

import devflow.agent.quality.CapabilitySurface;
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

    ExperienceFailureDisposition resolve(
            UiRuntimeContract contract,
            UiRuntimeContractValidation contractValidation,
            List<TestCaseSpec> plannedCases,
            List<TestCaseResult> caseResults,
            CoverageLedger coverageLedger
    ) {
        if (contractValidation != null && !contractValidation.valid()) {
            if (contractValidation.kind() == UiRuntimeContractValidationKind.PROBE_INVALID) {
                return new ExperienceFailureDisposition(
                        ExperienceFailureKind.RUNTIME_PROBE_INVALID,
                        "测试阶段的浏览器 probe 无效，当前结果不能继续用于实现修复。",
                        "请先修复测试侧 probe/collector 失败，再重新执行当前测试阶段。",
                        contractValidation.evidence(),
                        ImplementationPatchTarget.NONE,
                        List.of(),
                        missingRequiredSurfaces(coverageLedger),
                        ReviewRevisionRoute.REQUEST_HUMAN,
                        ReviewReasonCode.RUNTIME_PROBE_INVALID
                );
            }
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.OBSERVATION_CONTRACT_INVALID,
                    "测试阶段缺少可执行的运行时观测契约。",
                    "请修复真实运行时表面或交互后的可观察状态变化，再重新执行测试。",
                    contractValidation.evidence(),
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                    ownerScopedOverrides(contract, "修复运行时表面与可观察状态变化"),
                    missingRequiredSurfaces(coverageLedger),
                    ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                    ReviewReasonCode.OBSERVATION_CONTRACT_INVALID
            );
        }

        if (coverageMissingWithoutCases(coverageLedger, plannedCases)) {
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.TEST_CASE_INCOMPLETE,
                    "测试阶段缺少必需能力项的可执行用例覆盖。",
                    "补齐缺失的必需能力测试用例并重新执行测试。",
                    "missingRequiredCoverageWithoutCases=" + String.join(", ", missingRequiredSurfaces(coverageLedger)),
                    ImplementationPatchTarget.NONE,
                    List.of(),
                    missingRequiredSurfaces(coverageLedger),
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
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                    ownerScopedOverrides(contract, "修复观测主表面与交互后的状态变化"),
                    missingRequiredSurfaces(coverageLedger),
                    ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                    ReviewReasonCode.OBSERVATION_CONTRACT_INVALID
            );
        }

        if (hasRequiredFailures(caseResults) || (coverageLedger != null && coverageLedger.hasMissingRequiredCoverage())) {
            return new ExperienceFailureDisposition(
                    ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                    "当前实现仍缺少关键体验能力的通过证据。",
                    "请在实现阶段补齐缺失能力，并重新执行测试。",
                    firstRequiredFailureEvidence(caseResults, coverageLedger),
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                    ownerScopedOverrides(contract, "修复缺失体验能力与可观察反馈"),
                    missingRequiredSurfaces(coverageLedger),
                    ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
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
                .anyMatch(result -> "missing-runtime-element".equals(result.failureReason())
                        || "setup-failure".equals(result.failureReason()));
    }

    private String firstObservationFailureEvidence(List<TestCaseResult> caseResults) {
        if (caseResults == null) {
            return "";
        }
        return caseResults.stream()
                .filter(result -> result != null && result.required())
                .filter(result -> "missing-runtime-element".equals(result.failureReason())
                        || "setup-failure".equals(result.failureReason()))
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

    private List<String> missingRequiredSurfaces(CoverageLedger coverageLedger) {
        if (coverageLedger == null || coverageLedger.entries() == null || coverageLedger.entries().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> surfaces = new LinkedHashSet<>();
        for (CoverageLedgerEntry entry : coverageLedger.entries()) {
            if (entry == null || !entry.missingRequired() || entry.surface() == null) {
                continue;
            }
            surfaces.add(entry.surface().wireValue());
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
}
