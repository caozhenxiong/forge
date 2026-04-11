package devflow.agent.executor;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 检查 HTML 入口是否把同目录脚本/样式资产真正接到了运行时。
 */
final class WebRuntimeAssetWiringInspector {

    private final FileProjectWorkspace workspace;
    private final RuntimeScriptGraphInspector runtimeScriptGraphInspector;

    WebRuntimeAssetWiringInspector(FileProjectWorkspace workspace) {
        this.workspace = workspace;
        this.runtimeScriptGraphInspector = new RuntimeScriptGraphInspector(workspace);
    }

    WebRuntimeAssetWiringInspection inspect(
            Path projectPath,
            Path htmlEntryPath,
            HtmlStructureSnapshot snapshot,
            String htmlSource
    ) {
        RuntimeScriptGraphInspector.RuntimeScriptGraph runtimeGraph =
                runtimeScriptGraphInspector.inspectProject(projectPath, htmlEntryPath);
        Set<Path> directHtmlRuntimeScripts = runtimeScriptGraphInspector.resolveDirectHtmlRuntimeScripts(
                htmlEntryPath,
                htmlSource,
                runtimeGraph.runtimeScripts()
        );
        Set<Path> inlineImportedRuntimeScripts = runtimeScriptGraphInspector.resolveInlineImportedRuntimeScripts(
                htmlEntryPath,
                htmlSource,
                runtimeGraph.runtimeScripts()
        );
        Set<Path> externallyReachableRuntimeScripts = runtimeScriptGraphInspector.expandReachable(
                directHtmlRuntimeScripts,
                runtimeGraph
        );
        Set<Path> inlineReachableRuntimeScripts = runtimeScriptGraphInspector.expandReachable(
                inlineImportedRuntimeScripts,
                runtimeGraph
        );
        Set<Path> siblingStyles = runtimeAssetsUnderEntry(projectPath, htmlEntryPath, devflow.agent.util.ProjectPathSupport::isStyleAsset);
        Set<Path> orphanRuntimeScripts = new LinkedHashSet<>(runtimeGraph.runtimeScripts());
        orphanRuntimeScripts.removeAll(externallyReachableRuntimeScripts);
        orphanRuntimeScripts.removeAll(inlineReachableRuntimeScripts);
        List<String> issues = new java.util.ArrayList<>();
        List<String> evidence = new java.util.ArrayList<>();
        List<String> referencedStyles = HtmlDocumentInspector.referencedStylesheetPaths(htmlSource);
        if (!orphanRuntimeScripts.isEmpty()) {
            issues.add("HTML 入口存在本地运行脚本资源，但入口没有把这些脚本接入运行时。");
            evidence.add("入口目录树下未接线的运行脚本文件: " + joinPaths(orphanRuntimeScripts));
        }
        if (!siblingStyles.isEmpty() && referencedStyles.isEmpty() && snapshot.inlineStyleCount() == 0) {
            issues.add("HTML 入口存在本地样式资源，但没有任何样式接线。");
            evidence.add("入口目录树下的样式文件: " + joinPaths(siblingStyles));
        }
        List<Path> directRoots = runtimeScriptGraphInspector.selectRootScripts(directHtmlRuntimeScripts, runtimeGraph);
        List<Path> orphanRoots = runtimeScriptGraphInspector.selectRootScripts(orphanRuntimeScripts, runtimeGraph);
        return new WebRuntimeAssetWiringInspection(
                runtimeGraph.runtimeScripts().stream().sorted().toList(),
                directRoots,
                inlineImportedRuntimeScripts.stream().sorted().toList(),
                orphanRoots,
                List.copyOf(issues),
                List.copyOf(evidence)
        );
    }

    private Set<Path> runtimeAssetsUnderEntry(Path projectPath, Path htmlEntryPath, Predicate<Path> predicate) {
        Path parent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        boolean rootEntry = parent.toString().isBlank();
        Set<Path> results = new LinkedHashSet<>();
        for (Path relativePath : workspace.listProjectFiles(projectPath)) {
            String normalized = relativePath.toString().replace('\\', '/');
            if (normalized.startsWith(".devflow/")) {
                continue;
            }
            Path candidate = relativePath.normalize();
            Path candidateParent = candidate.getParent() == null ? Path.of("") : candidate.getParent().normalize();
            if (!rootEntry && !candidateParent.equals(parent) && !candidateParent.startsWith(parent)) {
                continue;
            }
            if (predicate.test(candidate)) {
                results.add(candidate);
            }
        }
        return results;
    }

    private String joinPaths(Set<Path> paths) {
        return paths.stream().map(Path::toString).sorted().reduce((left, right) -> left + ", " + right).orElse("");
    }
}
