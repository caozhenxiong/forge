package devflow.agent.executor;

import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CapabilitySurface;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 基于浏览器快照和 runtime wiring 检查，解析 TEST 阶段唯一运行时 contract。
 */
final class UiRuntimeContractResolver {

    private final FileProjectWorkspace workspace;
    private final TreeSitterSupport treeSitterSupport;
    private final WebRuntimeWiringCheck webRuntimeWiringCheck;

    UiRuntimeContractResolver(FileProjectWorkspace workspace, TreeSitterSupport treeSitterSupport) {
        this.workspace = workspace;
        this.treeSitterSupport = treeSitterSupport;
        this.webRuntimeWiringCheck = new WebRuntimeWiringCheck(workspace);
    }

    UiRuntimeContract resolve(
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
        UiObservationTarget primarySurface = selectPrimarySurfaceTarget(runtimeSnapshot, ownerPaths, qualityPlan);
        List<UiObservationTarget> targets = buildObservationTargets(primarySurface, ownerPaths, qualityPlan);
        return new UiRuntimeContract(entryPath, ownerPaths, List.of(), targets);
    }

    UiRuntimeContract enrichRunStateEntryTargets(UiRuntimeContract contract, List<TestCaseSpec> cases) {
        if (contract == null) {
            return UiRuntimeContract.empty();
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>();
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
                    targets.add(step.selector().trim());
                }
            }
        }
        return contract.withRunStateEntryTargets(List.copyOf(targets));
    }

    UiRuntimeContractValidation validate(
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
        for (CapabilitySurface surface : requiredObservedSurfaces(qualityPlan)) {
            UiObservationTarget target = contract.targetFor(surface);
            if (target == null || !target.usable()) {
                missingTargetIssues.add("UI runtime contract is missing observation target for required surface: " + surface.wireValue());
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

    private UiObservationTarget selectPrimarySurfaceTarget(
            RuntimeSnapshot runtimeSnapshot,
            List<String> ownerPaths,
            QualityPlan qualityPlan
    ) {
        RuntimeSurfaceCandidate canvasCandidate = largestCandidate(runtimeSnapshot, UiObservationMode.CANVAS_HASH);
        RuntimeSurfaceCandidate domCandidate = largestCandidate(runtimeSnapshot, UiObservationMode.DOM_SIGNATURE);
        RuntimeSurfaceCandidate selected;
        if (canvasCandidate == null) {
            selected = domCandidate;
        } else if (domCandidate == null) {
            selected = canvasCandidate;
        } else {
            selected = canvasCandidate.area() >= domCandidate.area() ? canvasCandidate : domCandidate;
        }
        if (selected == null || !selected.usable()) {
            return null;
        }
        return new UiObservationTarget(
                CapabilitySurface.PRIMARY_VISUAL_SURFACE,
                selected.selector(),
                selected.mode(),
                ownerPaths,
                requiredObservedSurfaces(qualityPlan).contains(CapabilitySurface.PRIMARY_VISUAL_SURFACE)
        );
    }

    private List<UiObservationTarget> buildObservationTargets(
            UiObservationTarget primarySurface,
            List<String> ownerPaths,
            QualityPlan qualityPlan
    ) {
        if (primarySurface == null) {
            return List.of();
        }
        LinkedHashSet<CapabilitySurface> requiredSurfaces = new LinkedHashSet<>(requiredObservedSurfaces(qualityPlan));
        LinkedHashSet<UiObservationTarget> targets = new LinkedHashSet<>();
        targets.add(primarySurface);
        for (CapabilitySurface surface : requiredSurfaces) {
            if (surface == CapabilitySurface.PRIMARY_VISUAL_SURFACE) {
                continue;
            }
            targets.add(new UiObservationTarget(surface, primarySurface.selector(), primarySurface.mode(), ownerPaths, true));
        }
        return List.copyOf(targets);
    }

    private List<CapabilitySurface> requiredObservedSurfaces(QualityPlan qualityPlan) {
        if (qualityPlan == null || qualityPlan.capabilityMatrix() == null) {
            return List.of();
        }
        return qualityPlan.capabilityMatrix().requiredSurfaces().stream()
                .filter(CapabilitySurface::requiresObservationTarget)
                .toList();
    }

    private RuntimeSurfaceCandidate largestCandidate(RuntimeSnapshot runtimeSnapshot, UiObservationMode mode) {
        if (runtimeSnapshot == null || runtimeSnapshot.surfaceCandidates() == null || runtimeSnapshot.surfaceCandidates().isEmpty()) {
            return null;
        }
        RuntimeSurfaceCandidate largest = null;
        for (RuntimeSurfaceCandidate candidate : runtimeSnapshot.surfaceCandidates()) {
            if (candidate == null || candidate.mode() != mode || !candidate.usable()) {
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
}
