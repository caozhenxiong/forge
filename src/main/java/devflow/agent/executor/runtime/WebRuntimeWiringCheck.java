package devflow.agent.executor.runtime;

import devflow.agent.executor.*;

import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WebRuntimeWiringCheck {
    private final WebRuntimeAssetWiringInspector assetWiringInspector;
    private final HtmlEntryRuntimeOwnershipInspector runtimeOwnershipInspector;

    public WebRuntimeWiringCheck(FileProjectWorkspace workspace) {
        this.assetWiringInspector = new WebRuntimeAssetWiringInspector(workspace);
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

        return issues.isEmpty()
                ? WebRuntimeWiringResult.success(htmlEntryPath, ownershipInspection)
                : WebRuntimeWiringResult.failure(htmlEntryPath, issues, evidence, ownershipInspection);
    }
}
