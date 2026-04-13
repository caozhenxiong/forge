package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.JavaScriptLiteralScanner;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 负责 HTML 宿主结构和内联脚本的确定性校验。
 */
final class GeneratedHtmlContentValidator {

    private final GeneratedJavaScriptContentValidator javaScriptContentValidator;
    private final HtmlEntryRuntimeOwnershipInspector runtimeOwnershipInspector = new HtmlEntryRuntimeOwnershipInspector();

    GeneratedHtmlContentValidator(GeneratedJavaScriptContentValidator javaScriptContentValidator) {
        this.javaScriptContentValidator = javaScriptContentValidator;
    }

    GeneratedContentValidationFailure validate(
            Path projectPath,
            Path relativePath,
            String content,
            HtmlRuntimeOwnershipContract runtimeContract,
            java.util.List<Path> relatedPaths
    ) {
        GeneratedContentValidationFailure structureFailure = validateStructure(content);
        if (structureFailure != null) {
            return structureFailure;
        }
        GeneratedContentValidationFailure externalizedRuntimeFailure = validateRuntimeOwnership(
                projectPath,
                relativePath,
                content,
                runtimeContract,
                relatedPaths
        );
        if (externalizedRuntimeFailure != null) {
            return externalizedRuntimeFailure;
        }
        GeneratedContentValidationFailure runtimeWiringFailure = validateHeadRuntimeScriptWiring(content);
        if (runtimeWiringFailure != null) {
            return runtimeWiringFailure;
        }
        return validateInlineScripts(projectPath, content);
    }

    private GeneratedContentValidationFailure validateStructure(String content) {
        if (!HtmlDocumentInspector.hasExplicitDocumentSkeleton(content)) {
            return new GeneratedContentValidationFailure(
                    GeneratedContentValidationCode.HTML_STRUCTURE_INVALID,
                    "HTML 结构不完整，缺少 <html> 或 </html>"
            );
        }
        if (!HtmlDocumentInspector.hasBalancedExplicitTagPairs(content, "script")) {
            return new GeneratedContentValidationFailure(
                    GeneratedContentValidationCode.HTML_STRUCTURE_INVALID,
                    "HTML 中 <script> 标签未闭合"
            );
        }
        if (!HtmlDocumentInspector.hasBalancedExplicitTagPairs(content, "style")) {
            return new GeneratedContentValidationFailure(
                    GeneratedContentValidationCode.HTML_STRUCTURE_INVALID,
                    "HTML 中 <style> 标签未闭合"
            );
        }
        return null;
    }

    private GeneratedContentValidationFailure validateInlineScripts(Path projectPath, String content) {
        int index = 0;
        for (String scriptBody : HtmlDocumentInspector.inlineScriptBodies(content)) {
            String trimmed = scriptBody.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            index++;
            GeneratedContentValidationFailure failure = javaScriptContentValidator.validate(
                    projectPath,
                    ProjectPathSupport.inlineScriptVirtualPath(),
                    trimmed
            );
            if (failure != null) {
                return new GeneratedContentValidationFailure(
                        GeneratedContentValidationCode.INLINE_SCRIPT_INVALID,
                        "内联脚本 #" + index + " 不可解析: " + failure.message()
                );
            }
        }
        return null;
    }

    private GeneratedContentValidationFailure validateRuntimeOwnership(
            Path projectPath,
            Path relativePath,
            String content,
            HtmlRuntimeOwnershipContract runtimeContract,
            java.util.List<Path> relatedPaths
    ) {
        if (relativePath == null || !ProjectPathSupport.isHtml(relativePath)) {
            return null;
        }
        HtmlRuntimeOwnershipContract effectiveContract = resolveRuntimeContract(relativePath, content, runtimeContract, relatedPaths);
        if (effectiveContract == null || !effectiveContract.active()) {
            return null;
        }
        HtmlEntryRuntimeOwnershipInspection inspection = runtimeOwnershipInspector.inspectDeclared(
                projectPath,
                content,
                effectiveContract,
                relatedPaths
        );
        if (inspection.passed()) {
            return null;
        }
        String details = inspection.summary();
        String evidenceMarkdown = inspection.evidenceMarkdown();
        if (!evidenceMarkdown.isBlank()) {
            details = details + " " + evidenceMarkdown;
        }
        return new GeneratedContentValidationFailure(
                GeneratedContentValidationCode.RUNTIME_WIRING_INVALID,
                details.trim()
        );
    }

    private HtmlRuntimeOwnershipContract resolveRuntimeContract(
            Path relativePath,
            String content,
            HtmlRuntimeOwnershipContract runtimeContract,
            java.util.List<Path> relatedPaths
    ) {
        if (runtimeContract != null && runtimeContract.active()) {
            return runtimeContract;
        }
        if (relatedPaths == null || relatedPaths.isEmpty()) {
            return null;
        }
        Set<Path> candidateRuntimePaths = relatedPaths.stream()
                .filter(path -> path != null && ProjectPathSupport.isRuntimeScript(path))
                .map(Path::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Path> runtimePaths = resolveReferencedRuntimePaths(relativePath, content, candidateRuntimePaths);
        if (runtimePaths.isEmpty()) {
            return null;
        }
        return HtmlRuntimeOwnershipContract.externalCompanion(relativePath.normalize(), runtimePaths.stream().sorted().toList());
    }

    private Set<Path> resolveReferencedRuntimePaths(
            Path relativePath,
            String content,
            Set<Path> candidateRuntimePaths
    ) {
        if (relativePath == null || candidateRuntimePaths == null || candidateRuntimePaths.isEmpty()) {
            return Set.of();
        }
        Path htmlParent = relativePath.getParent() == null ? Path.of("") : relativePath.getParent().normalize();
        Set<Path> resolved = new LinkedHashSet<>();
        for (String rawRef : HtmlDocumentInspector.referencedScriptPaths(content)) {
            addResolvedRuntimePath(resolved, candidateRuntimePaths, htmlParent, rawRef);
        }
        for (String inlineScript : HtmlDocumentInspector.inlineScriptBodies(content)) {
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
            String rawRef
    ) {
        if (rawRef == null || rawRef.isBlank() || ProjectPathSupport.isExternalReference(rawRef)) {
            return;
        }
        Path runtimePath = htmlParent.resolve(rawRef.trim()).normalize();
        if (candidateRuntimePaths.contains(runtimePath)) {
            resolved.add(runtimePath);
        }
    }

    private GeneratedContentValidationFailure validateHeadRuntimeScriptWiring(String content) {
        Document document = Jsoup.parse(content == null ? "" : content);
        for (Element script : document.select("head script[src]")) {
            String src = script.attr("src").trim();
            if (src.isBlank() || !isLocalRuntimeScript(src)) {
                continue;
            }
            if (script.hasAttr("defer") || script.hasAttr("async")) {
                continue;
            }
            if ("module".equalsIgnoreCase(script.attr("type").trim())) {
                continue;
            }
            return new GeneratedContentValidationFailure(
                    GeneratedContentValidationCode.RUNTIME_WIRING_INVALID,
                    "宿主 HTML 在 <head> 中同步加载本地运行脚本，必须显式使用 defer/async 或 module，避免 DOM 尚未就绪时提前执行。"
            );
        }
        return null;
    }

    private boolean isLocalRuntimeScript(String src) {
        if (src == null || src.isBlank()) {
            return false;
        }
        String normalized = src.trim();
        if (normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("//")
                || normalized.startsWith("data:")
                || normalized.startsWith("javascript:")) {
            return false;
        }
        return ProjectPathSupport.isRuntimeScriptAsset(Path.of(normalized).normalize());
    }
}
