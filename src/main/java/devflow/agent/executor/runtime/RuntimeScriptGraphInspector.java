package devflow.agent.executor.runtime;

import devflow.agent.executor.gate.*;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.JavaScriptLiteralScanner;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 构建 html-entry 目录树下 runtime 脚本的确定性依赖图。
 *
 * <p>这层只处理本地脚本文件之间的静态 import 关系，不负责判断 ownership，
 * 也不负责给出 review 结论。这样 wiring / ownership / planning 三层都可以复用
 * 同一份 runtime graph，而不是各写一套路径猜测。
 */
public final class RuntimeScriptGraphInspector {

    private final FileProjectWorkspace workspace;

    public RuntimeScriptGraphInspector(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    public RuntimeScriptGraph inspectProject(Path projectPath, Path htmlEntryPath) {
        Set<Path> runtimeScripts = runtimeAssetsUnderEntry(projectPath, htmlEntryPath);
        Map<Path, Set<Path>> imports = buildImports(projectPath, runtimeScripts);
        return new RuntimeScriptGraph(runtimeScripts, imports);
    }

    public Set<Path> resolveDirectHtmlRuntimeScripts(Path htmlEntryPath, String htmlSource, Set<Path> candidateScripts) {
        if (htmlEntryPath == null || candidateScripts == null || candidateScripts.isEmpty()) {
            return Set.of();
        }
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        Set<Path> resolved = new LinkedHashSet<>();
        for (String rawRef : HtmlDocumentInspector.referencedScriptPaths(htmlSource)) {
            Path candidate = resolveCandidatePath(htmlParent, rawRef, candidateScripts);
            if (candidate != null) {
                resolved.add(candidate);
            }
        }
        return Set.copyOf(resolved);
    }

    public Set<Path> resolveInlineImportedRuntimeScripts(Path htmlEntryPath, String htmlSource, Set<Path> candidateScripts) {
        if (htmlEntryPath == null || candidateScripts == null || candidateScripts.isEmpty()) {
            return Set.of();
        }
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        Set<Path> resolved = new LinkedHashSet<>();
        for (String inlineBody : HtmlDocumentInspector.inlineScriptBodies(htmlSource)) {
            for (String specifier : JavaScriptLiteralScanner.extractImportSpecifiers(inlineBody)) {
                Path candidate = resolveCandidatePath(htmlParent, specifier, candidateScripts);
                if (candidate != null) {
                    resolved.add(candidate);
                }
            }
        }
        return Set.copyOf(resolved);
    }

    public Set<Path> expandReachable(Set<Path> roots, RuntimeScriptGraph graph) {
        if (roots == null || roots.isEmpty() || graph == null) {
            return Set.of();
        }
        Set<Path> reachable = new LinkedHashSet<>();
        Deque<Path> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            Path current = pending.removeFirst();
            if (current == null || !reachable.add(current)) {
                continue;
            }
            for (Path child : graph.imports().getOrDefault(current, Set.of())) {
                pending.addLast(child);
            }
        }
        return Set.copyOf(reachable);
    }

    public List<Path> selectRootScripts(Set<Path> scripts, RuntimeScriptGraph graph) {
        if (scripts == null || scripts.isEmpty()) {
            return List.of();
        }
        Set<Path> selected = new LinkedHashSet<>(scripts);
        if (graph != null) {
            selected.removeAll(graph.importedScripts());
        }
        if (selected.isEmpty()) {
            selected = new LinkedHashSet<>(scripts);
        }
        return selected.stream()
                .sorted()
                .toList();
    }

    public List<Path> selectDeclaredRoots(Path projectPath, List<Path> declaredRuntimeScripts) {
        if (declaredRuntimeScripts == null || declaredRuntimeScripts.isEmpty()) {
            return List.of();
        }
        Set<Path> candidates = new LinkedHashSet<>();
        for (Path runtimeScript : declaredRuntimeScripts) {
            if (runtimeScript != null && ProjectPathSupport.isRuntimeScript(runtimeScript)) {
                candidates.add(runtimeScript.normalize());
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        RuntimeScriptGraph graph = new RuntimeScriptGraph(candidates, buildImports(projectPath, candidates));
        return selectRootScripts(candidates, graph);
    }

    private Set<Path> runtimeAssetsUnderEntry(Path projectPath, Path htmlEntryPath) {
        if (projectPath == null || htmlEntryPath == null) {
            return Set.of();
        }
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
            if (ProjectPathSupport.isRuntimeScript(candidate)) {
                results.add(candidate);
            }
        }
        return Set.copyOf(results);
    }

    private Map<Path, Set<Path>> buildImports(Path projectPath, Set<Path> runtimeScripts) {
        Map<Path, Set<Path>> imports = new LinkedHashMap<>();
        for (Path runtimeScript : runtimeScripts) {
            Path scriptParent = runtimeScript.getParent() == null ? Path.of("") : runtimeScript.getParent().normalize();
            Set<Path> resolvedImports = new LinkedHashSet<>();
            String source = "";
            try {
                source = workspace.readFile(projectPath, runtimeScript);
            } catch (RuntimeException ignored) {
                source = "";
            }
            for (String specifier : JavaScriptLiteralScanner.extractImportSpecifiers(source)) {
                Path candidate = resolveCandidatePath(scriptParent, specifier, runtimeScripts);
                if (candidate != null) {
                    resolvedImports.add(candidate);
                }
            }
            imports.put(runtimeScript, Set.copyOf(resolvedImports));
        }
        return Map.copyOf(imports);
    }

    private Path resolveCandidatePath(Path basePath, String rawRef, Set<Path> candidateScripts) {
        if (rawRef == null || rawRef.isBlank() || candidateScripts == null || candidateScripts.isEmpty()) {
            return null;
        }
        String normalizedRef = rawRef.trim();
        if (ProjectPathSupport.isExternalReference(normalizedRef)) {
            return null;
        }
        Path resolved = basePath.resolve(normalizedRef).normalize();
        if (candidateScripts.contains(resolved)) {
            return resolved;
        }
        return null;
    }

    public record RuntimeScriptGraph(
            Set<Path> runtimeScripts,
            Map<Path, Set<Path>> imports
    ) {

        public RuntimeScriptGraph {
            runtimeScripts = runtimeScripts == null ? Set.of() : Set.copyOf(runtimeScripts);
            imports = imports == null ? Map.of() : Map.copyOf(imports);
        }

        public Set<Path> importedScripts() {
            Set<Path> imported = new LinkedHashSet<>();
            for (Set<Path> importedPaths : imports.values()) {
                imported.addAll(importedPaths);
            }
            return Set.copyOf(imported);
        }
    }
}
