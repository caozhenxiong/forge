package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 负责 HTML 宿主结构和内联脚本的确定性校验。
 */
public final class GeneratedHtmlContentValidator {

    private final GeneratedJavaScriptContentValidator javaScriptContentValidator;
    private final HtmlEntryRuntimeOwnershipInspector runtimeOwnershipInspector = new HtmlEntryRuntimeOwnershipInspector();
    private final HtmlRuntimeContractResolver runtimeContractResolver;

    public GeneratedHtmlContentValidator(GeneratedJavaScriptContentValidator javaScriptContentValidator) {
        this.javaScriptContentValidator = javaScriptContentValidator;
        this.runtimeContractResolver = new HtmlRuntimeContractResolver(new FileProjectWorkspace());
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
        HtmlRuntimeOwnershipContract effectiveContract = resolveRuntimeContract(
                projectPath,
                relativePath,
                content,
                runtimeContract,
                relatedPaths
        );
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
            Path projectPath,
            Path relativePath,
            String content,
            HtmlRuntimeOwnershipContract runtimeContract,
            java.util.List<Path> relatedPaths
    ) {
        return runtimeContractResolver.resolveCanonicalContract(
                projectPath,
                relativePath,
                runtimeContract,
                java.util.List.of(),
                content,
                relatedPaths
        );
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
