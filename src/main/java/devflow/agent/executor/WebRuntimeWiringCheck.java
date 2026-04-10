package devflow.agent.executor;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.ArrayList;
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

        List<String> referencedScripts = HtmlDocumentInspector.referencedScriptPaths(htmlSource);
        HtmlEntryRuntimeOwnershipInspection ownershipInspection =
                runtimeOwnershipInspector.inspectWorkspace(projectPath, htmlEntryPath, htmlSource);
        if (!ownershipInspection.passed()) {
            issues.addAll(ownershipInspection.issues());
            evidence.addAll(ownershipInspection.evidence());
        }
        assetWiringInspector.inspect(projectPath, htmlEntryPath, snapshot, htmlSource, issues, evidence);
        selectorReferenceInspector.inspect(projectPath, htmlEntryPath, htmlSource, referencedScripts, issues, evidence);

        return issues.isEmpty()
                ? WebRuntimeWiringResult.success()
                : WebRuntimeWiringResult.failure(issues, evidence);
    }
}
