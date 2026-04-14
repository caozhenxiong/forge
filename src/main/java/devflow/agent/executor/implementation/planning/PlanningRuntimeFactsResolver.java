package devflow.agent.executor.implementation.planning;

import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
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

    PlanningRuntimeFactsResolver(FileProjectWorkspace workspace) {
        this.workspace = workspace == null ? new FileProjectWorkspace() : workspace;
    }

    PlanningRuntimeFacts resolve(PlanningRequest request) {
        if (request == null || request.fingerprint() == null || !request.fingerprint().hasResolvedHtmlEntry()) {
            return PlanningRuntimeFacts.empty();
        }
        Path htmlEntryPath = Path.of(request.fingerprint().resolvedHtmlEntryPath()).normalize();
        HtmlRuntimeOwnershipContract explicitContract = request.continuationConstraints() == null
                ? null
                : request.continuationConstraints().protectedRuntimeContract(htmlEntryPath.toString());
        if (explicitContract != null && explicitContract.active()) {
            return new PlanningRuntimeFacts(htmlEntryPath, explicitContract);
        }
        String htmlSource = readHtmlSource(request.runRecord() == null ? null : request.runRecord().projectPath(), htmlEntryPath);
        if (htmlSource.isBlank()) {
            return new PlanningRuntimeFacts(htmlEntryPath, null);
        }
        List<Path> runtimePaths = resolveObservedRuntimePaths(htmlEntryPath, htmlSource);
        HtmlRuntimeOwnershipContract observedContract = runtimePaths.isEmpty()
                ? HtmlRuntimeOwnershipContract.inlineHost(htmlEntryPath)
                : HtmlRuntimeOwnershipContract.externalCompanion(htmlEntryPath, runtimePaths);
        return new PlanningRuntimeFacts(htmlEntryPath, observedContract);
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
        Path runtimePath = resolveRuntimePath(htmlEntryPath, rawReference);
        if (runtimePath != null) {
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
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        Path runtimePath = normalizedReference.startsWith("/")
                ? Path.of(normalizedReference.substring(1)).normalize()
                : htmlParent.resolve(normalizedReference).normalize();
        return ProjectPathSupport.isRuntimeScript(runtimePath) ? runtimePath : null;
    }

    private String stripQueryAndFragment(String value) {
        int fragmentIndex = value.indexOf('#');
        String withoutFragment = fragmentIndex >= 0 ? value.substring(0, fragmentIndex) : value;
        int queryIndex = withoutFragment.indexOf('?');
        return queryIndex >= 0 ? withoutFragment.substring(0, queryIndex) : withoutFragment;
    }
}
