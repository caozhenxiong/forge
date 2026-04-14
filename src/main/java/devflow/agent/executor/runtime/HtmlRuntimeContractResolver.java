package devflow.agent.executor.runtime;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.JavaScriptLiteralScanner;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * implementation 侧 host entry runtime contract 的唯一 resolver。
 *
 * <p>优先级固定为：
 * 1. continuation scope
 * 2. accepted change-set
 * 3. execution-state facts
 *
 * <p>其中 1 和 2 在运行时已经折叠为当前 effective scope changes。
 * 下游只允许消费这里产出的 canonical contract，不能再各自从 change 列表、
 * HTML 内容或 project graph 单独重建一份。
 */
public final class HtmlRuntimeContractResolver {

    private final RuntimeScriptGraphInspector runtimeScriptGraphInspector;

    public HtmlRuntimeContractResolver(FileProjectWorkspace workspace) {
        FileProjectWorkspace effectiveWorkspace = workspace == null ? new FileProjectWorkspace() : workspace;
        this.runtimeScriptGraphInspector = new RuntimeScriptGraphInspector(effectiveWorkspace);
    }

    public HtmlRuntimeOwnershipContract resolveCanonicalContract(
            Path projectPath,
            Path htmlEntryPath,
            HtmlRuntimeOwnershipContract explicitContract,
            List<FileChange> scopeChanges,
            String htmlSource,
            List<Path> relatedPaths
    ) {
        if (htmlEntryPath == null || !ProjectPathSupport.isHtml(htmlEntryPath)) {
            return null;
        }
        Path normalizedHtmlEntry = htmlEntryPath.normalize();
        HtmlRuntimeOwnershipContract normalizedExplicit = normalizeExplicitContract(
                normalizedHtmlEntry,
                explicitContract,
                projectPath,
                htmlSource,
                relatedPaths
        );
        if (normalizedExplicit != null && normalizedExplicit.active()) {
            return normalizedExplicit;
        }
        HtmlRuntimeOwnershipContract scopedContract = resolveScopeContract(projectPath, normalizedHtmlEntry, scopeChanges);
        if (scopedContract != null && scopedContract.active()) {
            return scopedContract;
        }
        List<Path> factRuntimePaths = resolveRuntimePathsFromFacts(projectPath, normalizedHtmlEntry, htmlSource, relatedPaths);
        if (factRuntimePaths.isEmpty()) {
            return null;
        }
        return HtmlRuntimeOwnershipContract.externalCompanion(normalizedHtmlEntry, factRuntimePaths);
    }

    private HtmlRuntimeOwnershipContract normalizeExplicitContract(
            Path htmlEntryPath,
            HtmlRuntimeOwnershipContract explicitContract,
            Path projectPath,
            String htmlSource,
            List<Path> relatedPaths
    ) {
        if (explicitContract == null || !explicitContract.active() || explicitContract.runtimeOwnership() == null) {
            return null;
        }
        if (explicitContract.inlineHost()) {
            return HtmlRuntimeOwnershipContract.inlineHost(htmlEntryPath);
        }
        if (!explicitContract.runtimePaths().isEmpty()) {
            return HtmlRuntimeOwnershipContract.externalCompanion(htmlEntryPath, explicitContract.runtimePaths());
        }
        return HtmlRuntimeOwnershipContract.externalCompanion(
                htmlEntryPath,
                resolveRuntimePathsFromFacts(projectPath, htmlEntryPath, htmlSource, relatedPaths)
        );
    }

    private HtmlRuntimeOwnershipContract resolveScopeContract(
            Path projectPath,
            Path htmlEntryPath,
            List<FileChange> scopeChanges
    ) {
        FileChange scopedHtmlChange = resolveScopedHtmlChange(htmlEntryPath, scopeChanges);
        if (scopedHtmlChange == null
                || scopedHtmlChange.action() == ChangeAction.DELETE
                || scopedHtmlChange.runtimeOwnership() == null) {
            return null;
        }
        if (scopedHtmlChange.runtimeOwnership() == RuntimeOwnershipMode.INLINE_HOST) {
            return HtmlRuntimeOwnershipContract.inlineHost(htmlEntryPath);
        }
        List<Path> runtimeRoots = resolveDeclaredRuntimeRoots(projectPath, htmlEntryPath, scopeChanges);
        if (runtimeRoots.isEmpty()) {
            runtimeRoots = resolveRuntimePathsFromFacts(projectPath, htmlEntryPath, null, List.of());
        }
        return HtmlRuntimeOwnershipContract.externalCompanion(htmlEntryPath, runtimeRoots);
    }

    private FileChange resolveScopedHtmlChange(Path htmlEntryPath, List<FileChange> scopeChanges) {
        if (htmlEntryPath == null || scopeChanges == null || scopeChanges.isEmpty()) {
            return null;
        }
        return scopeChanges.stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .filter(change -> htmlEntryPath.equals(Path.of(change.path()).normalize()))
                .findFirst()
                .orElse(null);
    }

    private List<Path> resolveDeclaredRuntimeRoots(
            Path projectPath,
            Path htmlEntryPath,
            List<FileChange> scopeChanges
    ) {
        if (htmlEntryPath == null || scopeChanges == null || scopeChanges.isEmpty()) {
            return List.of();
        }
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        List<Path> declaredRuntimeScripts = scopeChanges.stream()
                .filter(change -> change != null
                        && change.action() != ChangeAction.DELETE
                        && change.path() != null
                        && !change.path().isBlank())
                .map(change -> Path.of(change.path()).normalize())
                .filter(ProjectPathSupport::isRuntimeScript)
                .filter(path -> isUnderHtmlEntryTree(path, htmlParent))
                .toList();
        if (declaredRuntimeScripts.isEmpty()) {
            return List.of();
        }
        List<Path> runtimeRoots = runtimeScriptGraphInspector.selectDeclaredRoots(projectPath, declaredRuntimeScripts);
        return runtimeRoots.isEmpty() ? declaredRuntimeScripts : runtimeRoots;
    }

    private List<Path> resolveRuntimePathsFromFacts(
            Path projectPath,
            Path htmlEntryPath,
            String htmlSource,
            List<Path> relatedPaths
    ) {
        List<Path> relatedRuntimeRoots = resolveRelatedRuntimeRoots(htmlEntryPath, htmlSource, relatedPaths);
        if (!relatedRuntimeRoots.isEmpty()) {
            return relatedRuntimeRoots;
        }
        if (projectPath == null || htmlEntryPath == null) {
            return List.of();
        }
        RuntimeScriptGraphInspector.RuntimeScriptGraph graph = runtimeScriptGraphInspector.inspectProject(projectPath, htmlEntryPath);
        return runtimeScriptGraphInspector.selectRootScripts(graph.runtimeScripts(), graph);
    }

    private List<Path> resolveRelatedRuntimeRoots(
            Path htmlEntryPath,
            String htmlSource,
            List<Path> relatedPaths
    ) {
        if (htmlEntryPath == null
                || htmlSource == null
                || htmlSource.isBlank()
                || relatedPaths == null
                || relatedPaths.isEmpty()) {
            return List.of();
        }
        Set<Path> candidateRuntimePaths = relatedPaths.stream()
                .filter(path -> path != null && ProjectPathSupport.isRuntimeScript(path))
                .map(Path::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (candidateRuntimePaths.isEmpty()) {
            return List.of();
        }
        Set<Path> resolvedRuntimePaths = resolveReferencedRuntimePaths(
                htmlEntryPath.normalize(),
                htmlSource,
                candidateRuntimePaths
        );
        return resolvedRuntimePaths.stream().sorted().toList();
    }

    private Set<Path> resolveReferencedRuntimePaths(
            Path htmlEntryPath,
            String htmlSource,
            Set<Path> candidateRuntimePaths
    ) {
        if (htmlEntryPath == null || candidateRuntimePaths == null || candidateRuntimePaths.isEmpty()) {
            return Set.of();
        }
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        Set<Path> resolved = new LinkedHashSet<>();
        for (String rawReference : HtmlDocumentInspector.referencedScriptPaths(htmlSource)) {
            addResolvedRuntimePath(resolved, candidateRuntimePaths, htmlParent, rawReference);
        }
        for (String inlineScript : HtmlDocumentInspector.inlineScriptBodies(htmlSource)) {
            for (String specifier : JavaScriptLiteralScanner.extractImportSpecifiers(inlineScript)) {
                addResolvedRuntimePath(resolved, candidateRuntimePaths, htmlParent, specifier);
            }
        }
        return Set.copyOf(resolved);
    }

    private void addResolvedRuntimePath(
            Set<Path> resolved,
            Set<Path> candidateRuntimePaths,
            Path htmlParent,
            String rawReference
    ) {
        if (rawReference == null || rawReference.isBlank() || ProjectPathSupport.isExternalReference(rawReference)) {
            return;
        }
        Path runtimePath = htmlParent.resolve(rawReference.trim()).normalize();
        if (candidateRuntimePaths.contains(runtimePath)) {
            resolved.add(runtimePath);
        }
    }

    private boolean isUnderHtmlEntryTree(Path candidatePath, Path htmlParent) {
        if (candidatePath == null) {
            return false;
        }
        Path candidateParent = candidatePath.getParent() == null ? Path.of("") : candidatePath.getParent().normalize();
        return htmlParent.toString().isBlank()
                || candidateParent.equals(htmlParent)
                || candidateParent.startsWith(htmlParent);
    }
}
