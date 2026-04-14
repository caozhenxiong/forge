package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureReport;
import devflow.agent.executor.generation.GenerationFailureType;

import devflow.agent.editing.precise.HtmlPreciseEditor;
import devflow.agent.editing.precise.HtmlPrecisePatch;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 内联脚本连续失败后的结构性改道策略。
 *
 * <p>当宿主 HTML 里的脚本单元已经缩到足够小、仍然稳定被截断时，
 * 不再继续在宿主文档里消耗预算，而是把脚本外提到独立文件，
 * 让后续编辑切回普通代码文件主链。
 */
public class InlineScriptExtractToFileStrategy {

    private final HtmlPreciseEditor htmlPreciseEditor;

    public InlineScriptExtractToFileStrategy(HtmlPreciseEditor htmlPreciseEditor) {
        this.htmlPreciseEditor = htmlPreciseEditor;
    }

    public boolean shouldExternalize(Path htmlPath, EditUnit unit, GenerationFailureReport report) {
        if (htmlPath == null || unit == null || report == null) {
            return false;
        }
        return ProjectPathSupport.isHtml(htmlPath)
                && unit.restrictsSymbols()
                && !unit.splittable()
                && report.failureType() == GenerationFailureType.OUTPUT_TRUNCATED;
    }

    public GeneratedFileOutput externalize(Path htmlPath, String existingHtml, String scriptContent) {
        Path scriptPath = ProjectPathSupport.extractedInlineScriptAssetPath(htmlPath);
        String scriptRef = "./" + scriptPath.getFileName();
        String rewrittenHtml = externalizeRuntimeScript(existingHtml, scriptRef);
        return new GeneratedFileOutput(
                rewrittenHtml,
                List.of(new GeneratedAuxiliaryWrite(scriptPath, scriptContent))
        );
    }

    private String externalizeRuntimeScript(String existingHtml, String scriptRef) {
        Document document = Jsoup.parse(existingHtml == null ? "" : existingHtml);
        document.outputSettings().prettyPrint(false);
        document.select("script#" + devflow.agent.parsing.TreeSitterSupport.APP_SCRIPT_ID).remove();
        boolean alreadyReferenced = document.select("script[src]").stream()
                .map(element -> element.attr("src").trim())
                .anyMatch(scriptRef::equals);
        if (!alreadyReferenced) {
            Element body = document.body();
            if (body != null) {
                body.appendElement("script").attr("src", scriptRef);
            }
        }
        String rewritten = document.outerHtml();
        if (rewritten != null && !rewritten.isBlank()) {
            return rewritten;
        }
        return htmlPreciseEditor.applyPatch(
                existingHtml,
                new HtmlPrecisePatch(
                        null,
                        null,
                        "",
                        null,
                        "<script src=\"" + scriptRef + "\"></script>"
                )
        );
    }
}
