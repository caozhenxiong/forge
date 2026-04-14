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
 * 3. 当前 host HTML 已观察到的结构化 wiring facts
 *
 * <p>其中 1 和 2 在运行时已经折叠为当前 effective scope changes。
 * 只有已经显式进入 host-entry 语义的 HTML 才允许进入这里；
 * 下游只允许消费这里产出的 canonical contract，不能再各自从 change 列表、
 * HTML 内容或目录扫描单独重建一份。
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
            Path resolvedHostEntryPath,
            HtmlRuntimeOwnershipContract explicitContract,
            List<FileChange> scopeChanges,
            String htmlSource,
            List<Path> relatedPaths
    ) {
        if (htmlEntryPath == null || !ProjectPathSupport.isHtml(htmlEntryPath)) {
            return null;
        }
        Path normalizedHtmlEntry = htmlEntryPath.normalize();
        FileChange scopedHtmlChange = resolveScopedHtmlChange(normalizedHtmlEntry, scopeChanges);
        if (!hostEntryDeclared(normalizedHtmlEntry, resolvedHostEntryPath, explicitContract, scopedHtmlChange)) {
            return null;
        }
        HtmlRuntimeOwnershipContract normalizedExplicit = normalizeExplicitContract(
                normalizedHtmlEntry,
                explicitContract,
                htmlSource,
                relatedPaths
        );
        if (normalizedExplicit != null && normalizedExplicit.active()) {
            return normalizedExplicit;
        }
        HtmlRuntimeOwnershipContract scopedContract = resolveScopeContract(
                projectPath,
                normalizedHtmlEntry,
                scopedHtmlChange,
                scopeChanges,
                htmlSource,
                relatedPaths
        );
        if (scopedContract != null && scopedContract.active()) {
            return scopedContract;
        }
        if (!matchesResolvedHostEntry(normalizedHtmlEntry, resolvedHostEntryPath)) {
            return null;
        }
        List<Path> factRuntimePaths = resolveRuntimePathsFromObservedFacts(normalizedHtmlEntry, htmlSource, relatedPaths);
        if (factRuntimePaths.isEmpty()) {
            return null;
        }
        return HtmlRuntimeOwnershipContract.externalCompanion(normalizedHtmlEntry, factRuntimePaths);
    }

    private HtmlRuntimeOwnershipContract normalizeExplicitContract(
            Path htmlEntryPath,
            HtmlRuntimeOwnershipContract explicitContract,
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
                resolveRuntimePathsFromObservedFacts(htmlEntryPath, htmlSource, relatedPaths)
        );
    }

    private HtmlRuntimeOwnershipContract resolveScopeContract(
            Path projectPath,
            Path htmlEntryPath,
            FileChange scopedHtmlChange,
            List<FileChange> scopeChanges,
            String htmlSource,
            List<Path> relatedPaths
    ) {
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
            runtimeRoots = resolveRuntimePathsFromObservedFacts(htmlEntryPath, htmlSource, relatedPaths);
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

    private boolean hostEntryDeclared(
            Path htmlEntryPath,
            Path resolvedHostEntryPath,
            HtmlRuntimeOwnershipContract explicitContract,
            FileChange scopedHtmlChange
    ) {
        if (htmlEntryPath == null) {
            return false;
        }
        if (explicitContract != null
                && explicitContract.active()
                && htmlEntryPath.equals(explicitContract.htmlEntryPath())) {
            return true;
        }
        if (scopedHtmlChange != null
                && scopedHtmlChange.action() != ChangeAction.DELETE
                && scopedHtmlChange.runtimeOwnership() != null) {
            return true;
        }
        return matchesResolvedHostEntry(htmlEntryPath, resolvedHostEntryPath);
    }

    private boolean matchesResolvedHostEntry(Path htmlEntryPath, Path resolvedHostEntryPath) {
        return htmlEntryPath != null
                && resolvedHostEntryPath != null
                && htmlEntryPath.equals(resolvedHostEntryPath.normalize());
    }

    private List<Path> resolveRuntimePathsFromObservedFacts(
            Path htmlEntryPath,
            String htmlSource,
            List<Path> relatedPaths
    ) {
        if (htmlEntryPath == null
                || htmlSource == null
                || htmlSource.isBlank()) {
            return List.of();
        }
        Set<Path> candidateRuntimePaths = (relatedPaths == null ? List.<Path>of() : relatedPaths).stream()
                .filter(path -> path != null && ProjectPathSupport.isRuntimeScript(path))
                .map(Path::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
        if (htmlEntryPath == null) {
            return Set.of();
        }
        Set<Path> resolved = new LinkedHashSet<>();
        for (String rawReference : HtmlDocumentInspector.referencedScriptPaths(htmlSource)) {
            addResolvedRuntimePath(resolved, candidateRuntimePaths, htmlEntryPath, rawReference);
        }
        for (String inlineScript : HtmlDocumentInspector.inlineScriptBodies(htmlSource)) {
            for (String specifier : JavaScriptLiteralScanner.extractImportSpecifiers(inlineScript)) {
                addResolvedRuntimePath(resolved, candidateRuntimePaths, htmlEntryPath, specifier);
            }
        }
        return Set.copyOf(resolved);
    }

    private void addResolvedRuntimePath(
            Set<Path> resolved,
            Set<Path> candidateRuntimePaths,
            Path htmlEntryPath,
            String rawReference
    ) {
        Path runtimePath = resolveRuntimePath(htmlEntryPath, rawReference);
        if (runtimePath == null) {
            return;
        }
        if (candidateRuntimePaths.isEmpty() || candidateRuntimePaths.contains(runtimePath)) {
            resolved.add(runtimePath);
        }
    }

    private Path resolveRuntimePath(Path htmlEntryPath, String rawReference) {
        if (htmlEntryPath == null || rawReference == null || rawReference.isBlank()) {
            return null;
        }
        String normalizedReference = stripQueryAndFragment(rawReference.trim());
        if (normalizedReference.isBlank() || ProjectPathSupport.isExternalReference(normalizedReference)) {
            return null;
        }
        Path runtimePath;
        if (normalizedReference.startsWith("/")) {
            String projectRelative = normalizedReference.substring(1).trim();
            if (projectRelative.isBlank()) {
                return null;
            }
            runtimePath = Path.of(projectRelative).normalize();
        } else {
            Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
            runtimePath = htmlParent.resolve(normalizedReference).normalize();
        }
        return ProjectPathSupport.isRuntimeScript(runtimePath) ? runtimePath : null;
    }

    private String stripQueryAndFragment(String rawReference) {
        if (rawReference == null || rawReference.isBlank()) {
            return "";
        }
        int queryIndex = rawReference.indexOf('?');
        int fragmentIndex = rawReference.indexOf('#');
        int cutIndex = -1;
        if (queryIndex >= 0 && fragmentIndex >= 0) {
            cutIndex = Math.min(queryIndex, fragmentIndex);
        } else if (queryIndex >= 0) {
            cutIndex = queryIndex;
        } else if (fragmentIndex >= 0) {
            cutIndex = fragmentIndex;
        }
        return cutIndex < 0 ? rawReference : rawReference.substring(0, cutIndex);
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
