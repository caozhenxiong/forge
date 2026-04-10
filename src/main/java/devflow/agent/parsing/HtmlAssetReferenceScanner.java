package devflow.agent.parsing;

import devflow.agent.util.ProjectPathSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 负责 HTML 中脚本/样式引用与内联脚本体的稳定提取。
 */
final class HtmlAssetReferenceScanner {

    List<String> referencedScriptPaths(Document document) {
        List<String> results = new ArrayList<>();
        for (Element script : document.select("script[src]")) {
            String value = script.attr("src").trim();
            if (isLocalAsset(value)) {
                results.add(value);
            }
        }
        return List.copyOf(results);
    }

    List<String> referencedStylesheetPaths(Document document) {
        List<String> results = new ArrayList<>();
        for (Element link : document.select("link[href]")) {
            if (!declaresStylesheet(link)) {
                continue;
            }
            String value = link.attr("href").trim();
            if (isLocalAsset(value)) {
                results.add(value);
            }
        }
        return List.copyOf(results);
    }

    List<String> inlineScriptBodies(Document document) {
        List<String> scripts = new ArrayList<>();
        for (Element script : document.select("script:not([src])")) {
            String body = script.data();
            if (body == null || body.isBlank()) {
                body = script.html();
            }
            if (body != null && !body.isBlank()) {
                scripts.add(body.trim());
            }
        }
        return List.copyOf(scripts);
    }

    private boolean declaresStylesheet(Element link) {
        String rel = link.attr("rel");
        if (rel == null || rel.isBlank()) {
            return false;
        }
        return rel.toLowerCase(Locale.ROOT).contains("stylesheet");
    }

    private boolean isLocalAsset(String value) {
        return value != null
                && !value.isBlank()
                && !ProjectPathSupport.isExternalReference(value);
    }
}
