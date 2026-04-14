package devflow.agent.executor.runtime;

import devflow.agent.executor.runtime.RuntimeSnapshot;
import devflow.agent.executor.runtime.RuntimeSurfaceCandidate;
import devflow.agent.executor.testing.TestCaseSpec;
import devflow.agent.executor.testing.TestStepSemantic;
import devflow.agent.executor.testing.TestStepSpec;
import devflow.agent.executor.testing.UiObservationMode;
import devflow.agent.executor.testing.UiObservationTarget;
import devflow.agent.executor.testing.UiRuntimeContract;
import devflow.agent.executor.testing.UiRuntimeContractValidation;
import devflow.agent.executor.testing.UiRuntimeContractValidationKind;

import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 基于浏览器快照和 runtime wiring 检查，解析 TEST 阶段唯一运行时 contract。
 */
public final class UiRuntimeContractResolver {

    private final FileProjectWorkspace workspace;
    private final TreeSitterSupport treeSitterSupport;
    private final WebRuntimeWiringCheck webRuntimeWiringCheck;

    public UiRuntimeContractResolver(FileProjectWorkspace workspace, TreeSitterSupport treeSitterSupport) {
        this.workspace = workspace;
        this.treeSitterSupport = treeSitterSupport;
        this.webRuntimeWiringCheck = new WebRuntimeWiringCheck(workspace);
    }

    public UiRuntimeContract resolve(
            Path projectPath,
            ProjectFingerprint fingerprint,
            QualityPlan qualityPlan,
            RuntimeSnapshot runtimeSnapshot
    ) {
        if (projectPath == null || fingerprint == null || !fingerprint.hasResolvedHtmlEntry()) {
            return UiRuntimeContract.empty();
        }
        String entryPath = fingerprint.resolvedHtmlEntryPath();
        Path htmlEntryPath = Path.of(entryPath);
        String htmlSource = workspace.readFile(projectPath, htmlEntryPath);
        HtmlStructureSnapshot htmlSnapshot = treeSitterSupport.inspectHtml(htmlSource);
        WebRuntimeWiringResult wiringResult = webRuntimeWiringCheck.inspect(projectPath, htmlEntryPath, htmlSnapshot, htmlSource);
        List<String> ownerPaths = resolveOwnerPaths(entryPath, wiringResult);
        List<UiObservationTarget> targets = buildObservationTargets(runtimeSnapshot, ownerPaths, qualityPlan);
        return new UiRuntimeContract(entryPath, ownerPaths, List.of(), targets);
    }

    public UiRuntimeContract enrichRunStateEntryTargets(UiRuntimeContract contract, RuntimeSnapshot runtimeSnapshot, List<TestCaseSpec> cases) {
        if (contract == null) {
            return UiRuntimeContract.empty();
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (contract.runStateEntryTargets() != null) {
            for (String selector : contract.runStateEntryTargets()) {
                if (isValidRunStateEntry(runtimeSnapshot, selector)) {
                    targets.add(selector.trim());
                }
            }
        }
        if (cases != null) {
            for (TestCaseSpec testCase : cases) {
                if (testCase == null || testCase.steps() == null) {
                    continue;
                }
                for (TestStepSpec step : testCase.steps()) {
                    if (step == null
                            || step.semantic() != TestStepSemantic.RUN_STATE_ENTRY
                            || step.selector() == null
                            || step.selector().isBlank()) {
                        continue;
                    }
                    if (isValidRunStateEntry(runtimeSnapshot, step.selector())) {
                        targets.add(step.selector().trim());
                    }
                }
            }
        }
        return contract.withRunStateEntryTargets(List.copyOf(targets));
    }

    public UiRuntimeContractValidation validate(
            Path projectPath,
            QualityPlan qualityPlan,
            RuntimeSnapshot runtimeSnapshot,
            UiRuntimeContract contract
    ) {
        if (contract == null) {
            return UiRuntimeContractValidation.failure(
                    UiRuntimeContractValidationKind.ENTRY_OR_OWNER_INVALID,
                    List.of("UI runtime contract is missing.")
            );
        }
        if (runtimeSnapshot == null) {
            return UiRuntimeContractValidation.failure(
                    UiRuntimeContractValidationKind.PROBE_INVALID,
                    List.of("UI runtime probe is unavailable.")
            );
        }
        if (runtimeSnapshot.probeInvalid()) {
            return UiRuntimeContractValidation.failure(
                    UiRuntimeContractValidationKind.PROBE_INVALID,
                    runtimeSnapshot.captureErrors().isEmpty()
                            ? List.of("UI runtime probe is invalid.")
                            : runtimeSnapshot.captureErrors()
            );
        }
        if (!runtimeSnapshot.probeCaptured()) {
            return UiRuntimeContractValidation.failure(
                    UiRuntimeContractValidationKind.PROBE_INVALID,
                    List.of("UI runtime probe did not produce captured browser facts.")
            );
        }
        List<String> issues = new ArrayList<>();
        if (contract.entryPath().isBlank()) {
            issues.add("UI runtime contract is missing entryPath.");
        }
        if (contract.ownerPaths().isEmpty()) {
            issues.add("UI runtime contract is missing ownerPaths.");
        }
        if (projectPath != null) {
            for (String ownerPath : contract.ownerPaths()) {
                if (!ownerPath.isBlank() && !java.nio.file.Files.exists(projectPath.resolve(ownerPath))) {
                    issues.add("UI runtime contract references a missing owner path: " + ownerPath);
                }
            }
        }
        if (!issues.isEmpty()) {
            return UiRuntimeContractValidation.failure(
                    UiRuntimeContractValidationKind.ENTRY_OR_OWNER_INVALID,
                    issues
            );
        }
        List<String> missingTargetIssues = new ArrayList<>();
        List<String> selectorUnavailableIssues = new ArrayList<>();
        java.util.Set<String> requiredTargetIds = requiredObservedTargetIds(qualityPlan);
        for (String selector : contract.runStateEntryTargets()) {
            if (!isValidRunStateEntry(runtimeSnapshot, selector)) {
                selectorUnavailableIssues.add("UI runtime contract run-state-entry selector is unavailable at runtime: " + selector);
            }
        }
        for (String targetId : requiredTargetIds) {
            UiObservationTarget target = contract.targetFor(targetId);
            if (target == null || !target.usable()) {
                missingTargetIssues.add("UI runtime contract is missing observation target for required surface: " + targetId);
                continue;
            }
            if (!selectorExists(runtimeSnapshot, target.selector())) {
                selectorUnavailableIssues.add("UI runtime contract selector is unavailable at runtime: " + target.selector());
            }
        }
        if (!missingTargetIssues.isEmpty()) {
            return UiRuntimeContractValidation.failure(
                    UiRuntimeContractValidationKind.MISSING_REQUIRED_OBSERVATION_TARGET,
                    missingTargetIssues
            );
        }
        if (!selectorUnavailableIssues.isEmpty()) {
            return UiRuntimeContractValidation.failure(
                    UiRuntimeContractValidationKind.SELECTOR_UNAVAILABLE,
                    selectorUnavailableIssues
            );
        }
        return UiRuntimeContractValidation.success();
    }

    private List<String> resolveOwnerPaths(String entryPath, WebRuntimeWiringResult wiringResult) {
        HtmlEntryRuntimeOwnershipInspection ownershipInspection = wiringResult == null ? null : wiringResult.ownershipInspection();
        HtmlRuntimeOwnershipContract runtimeContract = ownershipInspection == null ? null : ownershipInspection.runtimeContract();
        if (runtimeContract == null || !runtimeContract.active()) {
            return entryPath == null || entryPath.isBlank() ? List.of() : List.of(entryPath.replace('\\', '/'));
        }
        if (runtimeContract.externalCompanion() && !runtimeContract.runtimePaths().isEmpty()) {
            return runtimeContract.runtimePathStrings();
        }
        return List.of(runtimeContract.htmlEntryPath().toString().replace('\\', '/'));
    }

    private List<UiObservationTarget> buildObservationTargets(
            RuntimeSnapshot runtimeSnapshot,
            List<String> ownerPaths,
            QualityPlan qualityPlan
    ) {
        LinkedHashSet<UiObservationTarget> targets = new LinkedHashSet<>();
        appendObservationTarget(targets, CapabilityIds.PRIMARY_VISUAL_SURFACE, runtimeSnapshot, ownerPaths, requiredObservedTargetIds(qualityPlan));
        appendObservationTarget(targets, CapabilityIds.PRIMARY_INTERACTION, runtimeSnapshot, ownerPaths, requiredObservedTargetIds(qualityPlan));
        return List.copyOf(targets);
    }

    private void appendObservationTarget(
            LinkedHashSet<UiObservationTarget> targets,
            String capabilityId,
            RuntimeSnapshot runtimeSnapshot,
            List<String> ownerPaths,
            java.util.Set<String> requiredTargetIds
    ) {
        RuntimeSurfaceCandidate candidate = selectObservationCandidate(capabilityId, runtimeSnapshot);
        if (candidate == null || !candidate.usable()) {
            return;
        }
        targets.add(new UiObservationTarget(
                capabilityId,
                candidate.selector(),
                candidate.mode(),
                ownerPaths,
                requiredTargetIds.contains(capabilityId)
        ));
    }

    /**
     * visual surface 关注“主显示面”；
     * interaction/timed surface 关注“可观察的状态变化载体”。
     *
     * <p>因此交互与时间推进优先选 canvas hash 候选，不再把视觉面直接扩散成所有 surface 的统一 target。
     */
    private RuntimeSurfaceCandidate selectObservationCandidate(String capabilityId, RuntimeSnapshot runtimeSnapshot) {
        if (CapabilityIds.PRIMARY_VISUAL_SURFACE.equals(capabilityId)) {
            return largestCandidate(runtimeSnapshot, null);
        }
        if (CapabilityIds.PRIMARY_INTERACTION.equals(capabilityId)) {
            RuntimeSurfaceCandidate canvasCandidate = largestCandidate(runtimeSnapshot, UiObservationMode.CANVAS_HASH);
            return canvasCandidate != null ? canvasCandidate : largestCandidate(runtimeSnapshot, UiObservationMode.DOM_SIGNATURE);
        }
        return largestCandidate(runtimeSnapshot, UiObservationMode.DOM_SIGNATURE);
    }

    private java.util.Set<String> requiredObservedTargetIds(QualityPlan qualityPlan) {
        if (qualityPlan == null || qualityPlan.capabilityMatrix() == null) {
            return java.util.Set.of();
        }
        return qualityPlan.capabilityMatrix().requiredObservationTargetIds();
    }

    private RuntimeSurfaceCandidate largestCandidate(RuntimeSnapshot runtimeSnapshot, UiObservationMode mode) {
        if (runtimeSnapshot == null || runtimeSnapshot.surfaceCandidates() == null || runtimeSnapshot.surfaceCandidates().isEmpty()) {
            return null;
        }
        RuntimeSurfaceCandidate largest = null;
        for (RuntimeSurfaceCandidate candidate : runtimeSnapshot.surfaceCandidates()) {
            if (candidate == null || !candidate.usable()) {
                continue;
            }
            if (mode != null && candidate.mode() != mode) {
                continue;
            }
            if (largest == null || candidate.area() > largest.area()) {
                largest = candidate;
            }
        }
        return largest;
    }

    private boolean selectorExists(RuntimeSnapshot runtimeSnapshot, String selector) {
        if (runtimeSnapshot == null || selector == null || selector.isBlank()) {
            return false;
        }
        if (runtimeSnapshot.selectors() != null && runtimeSnapshot.selectors().contains(selector)) {
            return true;
        }
        if (runtimeSnapshot.surfaceCandidates() != null) {
            return runtimeSnapshot.surfaceCandidates().stream()
                    .anyMatch(candidate -> candidate != null && selector.equals(candidate.selector()));
        }
        return false;
    }

    private boolean isValidRunStateEntry(RuntimeSnapshot runtimeSnapshot, String selector) {
        return runtimeSnapshot != null
                && selector != null
                && !selector.isBlank()
                && runtimeSnapshot.hasControlSelector(selector);
    }
}
