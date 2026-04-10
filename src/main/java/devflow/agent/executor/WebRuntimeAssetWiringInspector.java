package devflow.agent.executor;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.JavaScriptLiteralScanner;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 检查 HTML 入口是否把同目录脚本/样式资产真正接到了运行时。
 */
final class WebRuntimeAssetWiringInspector {

    private final FileProjectWorkspace workspace;

    WebRuntimeAssetWiringInspector(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    void inspect(
            Path projectPath,
            Path htmlEntryPath,
            HtmlStructureSnapshot snapshot,
            String htmlSource,
            List<String> issues,
            List<String> evidence
    ) {
        Set<Path> reachableRuntimeScripts = runtimeAssetsUnderEntry(projectPath, htmlEntryPath, ProjectPathSupport::isRuntimeScriptAsset);
        Set<Path> siblingStyles = runtimeAssetsUnderEntry(projectPath, htmlEntryPath, ProjectPathSupport::isStyleAsset);
        List<String> referencedScripts = HtmlDocumentInspector.referencedScriptPaths(htmlSource);
        List<String> referencedStyles = HtmlDocumentInspector.referencedStylesheetPaths(htmlSource);
        boolean referencesLocalRuntime = referencesLocalRuntimeScripts(
                projectPath,
                htmlEntryPath,
                htmlSource,
                referencedScripts,
                reachableRuntimeScripts
        );

        if (!reachableRuntimeScripts.isEmpty() && !referencesLocalRuntime) {
            issues.add("HTML 入口存在本地运行脚本资源，但入口没有把这些脚本接入运行时。");
            evidence.add("入口目录树下的运行脚本文件: " + joinPaths(reachableRuntimeScripts));
        }
        if (!siblingStyles.isEmpty() && referencedStyles.isEmpty() && snapshot.inlineStyleCount() == 0) {
            issues.add("HTML 入口存在本地样式资源，但没有任何样式接线。");
            evidence.add("入口目录树下的样式文件: " + joinPaths(siblingStyles));
        }
    }

    private boolean referencesLocalRuntimeScripts(
            Path projectPath,
            Path htmlEntryPath,
            String htmlSource,
            List<String> referencedScripts,
            Set<Path> runtimeScripts
    ) {
        if (runtimeScripts == null || runtimeScripts.isEmpty()) {
            return true;
        }
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        Set<String> runtimeRelativeRefs = relativeRefs(runtimeScripts, htmlParent);
        Set<String> runtimeBaseNames = baseNames(runtimeScripts);
        Set<Path> visitedScripts = new LinkedHashSet<>();
        Deque<ScriptSource> pendingSources = new ArrayDeque<>();
        for (String referencedScript : referencedScripts) {
            Path normalized = htmlParent.resolve(referencedScript).normalize();
            if (runtimeScripts.contains(normalized) && visitedScripts.add(normalized)) {
                pendingSources.addLast(new ScriptSource(
                        normalized,
                        workspace.readFile(projectPath, normalized),
                        normalized.getParent() == null ? Path.of("") : normalized.getParent().normalize()
                ));
            }
            String normalizedRef = referencedScript.replace('\\', '/');
            if (runtimeRelativeRefs.contains(normalizedRef) || runtimeBaseNames.contains(baseName(normalizedRef))) {
                return true;
            }
        }
        for (String scriptBody : HtmlDocumentInspector.inlineScriptBodies(htmlSource)) {
            pendingSources.addLast(new ScriptSource(null, scriptBody, htmlParent));
        }
        while (!pendingSources.isEmpty()) {
            ScriptSource source = pendingSources.removeFirst();
            if (referencesKnownLocalModule(
                    projectPath,
                    source,
                    runtimeScripts,
                    runtimeRelativeRefs,
                    runtimeBaseNames,
                    visitedScripts,
                    pendingSources
            )) {
                return true;
            }
        }
        return false;
    }

    private boolean referencesKnownLocalModule(
            Path projectPath,
            ScriptSource source,
            Set<Path> runtimeScripts,
            Set<String> runtimeRelativeRefs,
            Set<String> runtimeBaseNames,
            Set<Path> visitedScripts,
            Deque<ScriptSource> pendingSources
    ) {
        for (String specifier : JavaScriptLiteralScanner.extractImportSpecifiers(source.content())) {
            if (specifier == null || specifier.isBlank()) {
                continue;
            }
            String normalized = specifier.replace('\\', '/').trim();
            Path resolved = source.parent().resolve(normalized).normalize();
            if (runtimeScripts.contains(resolved)) {
                if (visitedScripts.add(resolved)) {
                    pendingSources.addLast(new ScriptSource(
                            resolved,
                            workspace.readFile(projectPath, resolved),
                            resolved.getParent() == null ? Path.of("") : resolved.getParent().normalize()
                    ));
                }
                return true;
            }
            if (runtimeRelativeRefs.contains(normalized) || runtimeBaseNames.contains(baseName(normalized))) {
                return true;
            }
        }
        return false;
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

    private Set<String> relativeRefs(Set<Path> assets, Path htmlParent) {
        Set<String> refs = new LinkedHashSet<>();
        for (Path asset : assets) {
            refs.add(htmlParent.relativize(asset.normalize()).toString().replace('\\', '/'));
        }
        return refs;
    }

    private Set<String> baseNames(Set<Path> assets) {
        Set<String> names = new LinkedHashSet<>();
        for (Path asset : assets) {
            names.add(baseName(asset.getFileName().toString()));
        }
        return names;
    }

    private String baseName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String joinPaths(Set<Path> paths) {
        return paths.stream().map(Path::toString).sorted().reduce((left, right) -> left + ", " + right).orElse("");
    }

    private record ScriptSource(
            Path path,
            String content,
            Path parent
    ) {
    }
}
