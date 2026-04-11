package devflow.agent.executor;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 在宿主 HTML 已切到 companion runtime script 后，做确定性的宿主收口。
 *
 * <p>`precise-html` / `focused-html-region` 协议本身都不擅长删除现有脚本锚点；
 * 一旦模型已经正确接入外提脚本，这层就负责把旧的 `app-script` 锚点移除，
 * 避免继续把“协议做不到的删除动作”留给下一轮模型反复重试。
 */
final class ExternalizedRuntimeHostNormalizer {

    String normalize(Path relativePath, HtmlRuntimeOwnershipContract runtimeContract, String html) {
        if (relativePath == null || html == null || html.isBlank() || !ProjectPathSupport.isHtml(relativePath)) {
            return html;
        }
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        if (runtimeContract == null || !runtimeContract.externalCompanion()) {
            deduplicateDocument(document);
            String normalized = document.outerHtml();
            return normalized == null || normalized.isBlank() ? html : normalized;
        }
        document.select("script#" + TreeSitterSupport.APP_SCRIPT_ID).remove();
        deduplicateDocument(document);
        String normalized = document.outerHtml();
        return normalized == null || normalized.isBlank() ? html : normalized;
    }

    private void deduplicateDocument(Document document) {
        deduplicateHeadSingletons(document);
        deduplicateScriptSources(document);
        deduplicateIds(document);
    }

    private void deduplicateHeadSingletons(Document document) {
        deduplicateKeepingFirst(document.select("head meta[charset]"));
        deduplicateKeepingFirst(document.select("head title"));
        deduplicateViewportMeta(document);
    }

    private void deduplicateScriptSources(Document document) {
        Set<String> seenSources = new LinkedHashSet<>();
        for (Element script : document.select("script[src]")) {
            String normalizedSrc = script.attr("src").trim().replace('\\', '/');
            if (normalizedSrc.isBlank()) {
                continue;
            }
            String key = normalizedSrc.toLowerCase(Locale.ROOT);
            if (!seenSources.add(key)) {
                script.remove();
            }
        }
    }

    private void deduplicateIds(Document document) {
        Set<String> seenIds = new LinkedHashSet<>();
        for (Element element : document.select("[id]")) {
            String id = element.id().trim();
            if (id.isBlank()) {
                continue;
            }
            if (!seenIds.add(id)) {
                element.remove();
            }
        }
    }

    private void deduplicateViewportMeta(Document document) {
        if (document.head() == null) {
            return;
        }
        boolean seenViewport = false;
        for (Element meta : document.head().select("meta[name]")) {
            String name = meta.attr("name").trim();
            if (!"viewport".equalsIgnoreCase(name)) {
                continue;
            }
            if (!seenViewport) {
                seenViewport = true;
                continue;
            }
            meta.remove();
        }
    }

    private void deduplicateKeepingFirst(org.jsoup.select.Elements elements) {
        if (elements == null || elements.size() < 2) {
            return;
        }
        for (int index = 1; index < elements.size(); index++) {
            elements.get(index).remove();
        }
    }
}
