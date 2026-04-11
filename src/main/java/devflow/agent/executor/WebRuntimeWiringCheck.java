package devflow.agent.executor;

import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class WebRuntimeWiringCheck {
    private final WebRuntimeAssetWiringInspector assetWiringInspector;
    private final WebRuntimeSelectorReferenceInspector selectorReferenceInspector;
    private final HtmlEntryRuntimeOwnershipInspector runtimeOwnershipInspector;

    public WebRuntimeWiringCheck(FileProjectWorkspace workspace) {
        this.assetWiringInspector = new WebRuntimeAssetWiringInspector(workspace);
        this.selectorReferenceInspector = new WebRuntimeSelectorReferenceInspector(workspace);
        this.runtimeOwnershipInspector = new HtmlEntryRuntimeOwnershipInspector();
    }

    public WebRuntimeWiringResult inspect(Path projectPath, Path htmlEntryPath, HtmlStructureSnapshot snapshot, String htmlSource) {
        if (htmlEntryPath == null || snapshot == null) {
            return WebRuntimeWiringResult.success();
        }
        List<String> issues = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        WebRuntimeAssetWiringInspection assetInspection = assetWiringInspector.inspect(
                projectPath,
                htmlEntryPath,
                snapshot,
                htmlSource
        );
        HtmlEntryRuntimeOwnershipInspection ownershipInspection =
                runtimeOwnershipInspector.inspectWorkspace(projectPath, htmlEntryPath, htmlSource, assetInspection);
        if (!ownershipInspection.passed()) {
            issues.addAll(ownershipInspection.issues());
            evidence.addAll(ownershipInspection.evidence());
        }
        issues.addAll(assetInspection.issues());
        evidence.addAll(assetInspection.evidence());
        List<Path> referencedRuntimePaths = ownershipInspection.referencedRuntimePaths().isEmpty()
                ? mergeReferencedRuntimePaths(assetInspection)
                : ownershipInspection.referencedRuntimePaths();
        selectorReferenceInspector.inspect(
                projectPath,
                htmlEntryPath,
                htmlSource,
                referencedRuntimePaths,
                ownershipInspection.availableRuntimePaths(),
                issues,
                evidence
        );

        return issues.isEmpty()
                ? WebRuntimeWiringResult.success(htmlEntryPath, ownershipInspection)
                : WebRuntimeWiringResult.failure(htmlEntryPath, issues, evidence, ownershipInspection);
    }

    private List<Path> mergeReferencedRuntimePaths(WebRuntimeAssetWiringInspection inspection) {
        LinkedHashSet<Path> merged = new LinkedHashSet<>();
        if (inspection != null) {
            merged.addAll(inspection.directHtmlRuntimeScripts());
            merged.addAll(inspection.inlineImportedRuntimeScripts());
        }
        return merged.stream().sorted().toList();
    }
}
