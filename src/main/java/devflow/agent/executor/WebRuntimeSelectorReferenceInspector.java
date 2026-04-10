package devflow.agent.executor;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.JavaScriptLiteralScanner;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检查运行脚本引用的选择器是否真实存在于 HTML 宿主里。
 */
final class WebRuntimeSelectorReferenceInspector {

    private final FileProjectWorkspace workspace;

    WebRuntimeSelectorReferenceInspector(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    void inspect(
            Path projectPath,
            Path htmlEntryPath,
            String htmlSource,
            List<String> referencedScripts,
            List<String> issues,
            List<String> evidence
    ) {
        SelectorReferences references = collectSelectorReferences(projectPath, htmlEntryPath, htmlSource, referencedScripts);
        if (!references.missingIds().isEmpty()) {
            issues.add("运行脚本引用了 HTML 中不存在的 id 选择器。");
            references.missingIds().forEach(item -> evidence.add("missing id selector: #" + item));
        }
        if (!references.missingClasses().isEmpty()) {
            issues.add("运行脚本引用了 HTML 中不存在的 class 选择器。");
            references.missingClasses().forEach(item -> evidence.add("missing class selector: ." + item));
        }
    }

    private SelectorReferences collectSelectorReferences(
            Path projectPath,
            Path htmlEntryPath,
            String htmlSource,
            List<String> referencedScripts
    ) {
        Set<String> htmlIds = new LinkedHashSet<>(HtmlDocumentInspector.idSelectors(htmlSource));
        Set<String> htmlClasses = new LinkedHashSet<>(HtmlDocumentInspector.classSelectors(htmlSource));
        Set<String> referencedIds = new LinkedHashSet<>();
        Set<String> referencedClasses = new LinkedHashSet<>();

        for (String inlineScript : HtmlDocumentInspector.inlineScriptBodies(htmlSource)) {
            extractSelectorReferences(inlineScript, referencedIds, referencedClasses);
        }

        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        for (String scriptPath : referencedScripts) {
            Path resolved = htmlParent.resolve(scriptPath).normalize();
            if (!Files.exists(projectPath.resolve(resolved).normalize())) {
                continue;
            }
            extractSelectorReferences(workspace.readFile(projectPath, resolved), referencedIds, referencedClasses);
        }

        Set<String> missingIds = referencedIds.stream()
                .filter(id -> !htmlIds.contains(id))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        Set<String> missingClasses = referencedClasses.stream()
                .filter(className -> !htmlClasses.contains(className))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        return new SelectorReferences(missingIds, missingClasses);
    }

    private void extractSelectorReferences(String source, Set<String> ids, Set<String> classes) {
        for (String id : JavaScriptLiteralScanner.extractCallStringArguments(source, "getElementById")) {
            if (!id.isBlank()) {
                ids.add(id.trim());
            }
        }
        collectQuerySelectors(JavaScriptLiteralScanner.extractCallStringArguments(source, "querySelector"), ids, classes);
        collectQuerySelectors(JavaScriptLiteralScanner.extractCallStringArguments(source, "querySelectorAll"), ids, classes);
        for (String className : JavaScriptLiteralScanner.extractCallStringArguments(source, "getElementsByClassName")) {
            String normalized = className.trim();
            if (!normalized.isBlank()) {
                classes.add(normalized);
            }
        }
    }

    private void collectQuerySelectors(List<String> selectors, Set<String> ids, Set<String> classes) {
        for (String selector : selectors) {
            String normalized = selector.trim();
            if (normalized.startsWith("#")) {
                ids.add(normalized.substring(1));
            } else if (normalized.startsWith(".")) {
                classes.add(normalized.substring(1));
            }
        }
    }

    private record SelectorReferences(Set<String> missingIds, Set<String> missingClasses) {
    }
}
