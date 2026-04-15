package devflow.agent.executor.implementation.planning;

import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
import devflow.agent.executor.runtime.RuntimeScriptGraphInspector;
import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.JavaScriptLiteralScanner;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * planning runtime facts 的唯一装配入口。
 *
 * <p>这里只允许消费 continuation 已确认的 contract，以及当前 HTML 已观察到的结构化 wiring facts。
 * 明确禁止目录扫描、root-script 猜测、companion 文件名 fallback。
 */
final class PlanningRuntimeFactsResolver {

    private final FileProjectWorkspace workspace;
    private final RuntimeScriptGraphInspector runtimeScriptGraphInspector;

    PlanningRuntimeFactsResolver(FileProjectWorkspace workspace) {
        this.workspace = workspace == null ? new FileProjectWorkspace() : workspace;
        this.runtimeScriptGraphInspector = new RuntimeScriptGraphInspector(this.workspace);
    }

    PlanningRuntimeFacts resolve(PlanningRequest request) {
        if (request == null || request.fingerprint() == null || !request.fingerprint().hasResolvedHtmlEntry()) {
            return PlanningRuntimeFacts.empty();
        }
        Path htmlEntryPath = Path.of(request.fingerprint().resolvedHtmlEntryPath()).normalize();
        Path projectPath = request.runRecord() == null ? null : request.runRecord().projectPath();
        HtmlRuntimeOwnershipContract explicitContract = request.continuationConstraints() == null
                ? null
                : request.continuationConstraints().protectedRuntimeContract(htmlEntryPath.toString());
        if (explicitContract != null && explicitContract.active()) {
            List<Path> knownRuntimeRoots = resolveKnownRuntimeRoots(projectPath, explicitContract.runtimePaths());
            return new PlanningRuntimeFacts(
                    htmlEntryPath,
                    explicitContract,
                    resolveReachableRuntimePaths(projectPath, htmlEntryPath, explicitContract.runtimePaths()),
                    knownRuntimeRoots
            );
        }
        String htmlSource = readHtmlSource(projectPath, htmlEntryPath);
        if (htmlSource.isBlank()) {
            return new PlanningRuntimeFacts(htmlEntryPath, null, List.of(), List.of());
        }
        List<Path> runtimePaths = resolveObservedRuntimePaths(htmlEntryPath, htmlSource);
        HtmlRuntimeOwnershipContract observedContract = runtimePaths.isEmpty()
                ? HtmlRuntimeOwnershipContract.inlineHost(htmlEntryPath)
                : HtmlRuntimeOwnershipContract.externalCompanion(htmlEntryPath, runtimePaths);
        List<Path> knownRuntimeRoots = resolveKnownRuntimeRoots(projectPath, observedContract.runtimePaths());
        return new PlanningRuntimeFacts(
                htmlEntryPath,
                observedContract,
                resolveReachableRuntimePaths(projectPath, htmlEntryPath, observedContract.runtimePaths()),
                knownRuntimeRoots
        );
    }

    private String readHtmlSource(Path projectPath, Path htmlEntryPath) {
        if (projectPath == null || htmlEntryPath == null) {
            return "";
        }
        try {
            return workspace.readFile(projectPath, htmlEntryPath);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private List<Path> resolveObservedRuntimePaths(Path htmlEntryPath, String htmlSource) {
        Set<Path> resolved = new LinkedHashSet<>();
        for (String reference : HtmlDocumentInspector.referencedScriptPaths(htmlSource)) {
            addRuntimePath(resolved, htmlEntryPath, reference);
        }
        for (String inlineBody : HtmlDocumentInspector.inlineScriptBodies(htmlSource)) {
            for (String specifier : JavaScriptLiteralScanner.extractImportSpecifiers(inlineBody)) {
                addRuntimePath(resolved, htmlEntryPath, specifier);
            }
        }
        return List.copyOf(resolved);
    }

    private void addRuntimePath(Set<Path> resolved, Path htmlEntryPath, String rawReference) {
        Path runtimePath = runtimeScriptGraphInspector.resolveProjectRelativeReference(htmlEntryPath, rawReference);
        if (runtimePath != null) {
            if (ProjectPathSupport.isRuntimeScript(runtimePath)) {
                resolved.add(runtimePath);
            }
        }
    }

    private List<Path> resolveReachableRuntimePaths(
            Path projectPath,
            Path htmlEntryPath,
            List<Path> runtimeRoots
    ) {
        if (runtimeRoots == null || runtimeRoots.isEmpty()) {
            return List.of();
        }
        Set<Path> normalizedRoots = new LinkedHashSet<>();
        for (Path runtimeRoot : runtimeRoots) {
            if (runtimeRoot != null) {
                normalizedRoots.add(runtimeRoot.normalize());
            }
        }
        if (normalizedRoots.isEmpty() || projectPath == null || htmlEntryPath == null) {
            return List.copyOf(normalizedRoots);
        }
        RuntimeScriptGraphInspector.RuntimeScriptGraph runtimeGraph =
                runtimeScriptGraphInspector.inspectProject(projectPath, htmlEntryPath);
        return runtimeScriptGraphInspector.expandReachable(normalizedRoots, runtimeGraph).stream()
                .sorted()
                .toList();
    }

    private List<Path> resolveKnownRuntimeRoots(Path projectPath, List<Path> declaredRuntimePaths) {
        if (declaredRuntimePaths == null || declaredRuntimePaths.isEmpty()) {
            return List.of();
        }
        if (projectPath == null) {
            return declaredRuntimePaths.stream()
                    .filter(path -> path != null)
                    .map(Path::normalize)
                    .distinct()
                    .toList();
        }
        return runtimeScriptGraphInspector.selectDeclaredRoots(projectPath, declaredRuntimePaths);
    }
}
