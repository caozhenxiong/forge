package devflow.agent.parsing;

import devflow.agent.util.ProjectPathSupport;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 用稳定 HTML 解析器读取运行时入口结构。
 *
 * <p>这里不承担任何业务语义判断，只提供：
 * 1. 脚本/样式引用提取；
 * 2. 内联脚本提取；
 * 3. id/class 选择器收集；
 * 4. 运行表面相关标签存在性判断。
 */
public final class HtmlDocumentInspector {

    private static final HtmlAssetReferenceScanner ASSET_REFERENCE_SCANNER = new HtmlAssetReferenceScanner();
    private static final HtmlSelectorScanner SELECTOR_SCANNER = new HtmlSelectorScanner();
    private static final HtmlFragmentInspectorSupport FRAGMENT_SUPPORT = new HtmlFragmentInspectorSupport();

    private HtmlDocumentInspector() {
    }

    public static List<String> referencedScriptPaths(String htmlSource) {
        return ASSET_REFERENCE_SCANNER.referencedScriptPaths(parse(htmlSource));
    }

    public static List<String> referencedStylesheetPaths(String htmlSource) {
        return ASSET_REFERENCE_SCANNER.referencedStylesheetPaths(parse(htmlSource));
    }

    public static List<String> inlineScriptBodies(String htmlSource) {
        return ASSET_REFERENCE_SCANNER.inlineScriptBodies(parse(htmlSource));
    }

    public static Set<String> idSelectors(String htmlSource) {
        return SELECTOR_SCANNER.idSelectors(parse(htmlSource));
    }

    public static Set<String> classSelectors(String htmlSource) {
        return SELECTOR_SCANNER.classSelectors(parse(htmlSource));
    }

    public static boolean hasRuntimeSurfaceTags(String htmlSource) {
        Document document = parse(htmlSource);
        return !document.select("main, canvas, section, article, div, button, h1").isEmpty();
    }

    public static boolean hasExplicitDocumentSkeleton(String htmlSource) {
        return FRAGMENT_SUPPORT.hasExplicitDocumentSkeleton(htmlSource);
    }

    public static boolean hasBalancedExplicitTagPairs(String htmlSource, String tagName) {
        return FRAGMENT_SUPPORT.hasBalancedExplicitTagPairs(htmlSource, tagName);
    }

    /**
     * 如果片段本身就是一个单独包裹的结构标签，提取其内部内容。
     *
     * <p>这是结构化归一，不是自然语言猜测：
     * 1. `focused-html-region:script` 偶尔会收到 `<script>...</script>` 包裹；
     * 2. `style` / `markup` 也可能多包一层 `<style>` / `<main>`；
     * 3. 这里用 HTML 解析树确认“是否真的是单一包裹标签”，然后安全地提取内部 body。
     *
     * @return 单一包裹标签的内部内容；如果不是稳定的单标签片段，则返回 null。
     */
    public static String unwrapSingleWrappedElementBody(String htmlFragment, String tagName) {
        return FRAGMENT_SUPPORT.unwrapSingleWrappedElementBody(htmlFragment, tagName);
    }

    public static boolean containsElementTag(String htmlFragment, String tagName) {
        return FRAGMENT_SUPPORT.containsElementTag(htmlFragment, tagName);
    }

    private static Document parse(String htmlSource) {
        return Jsoup.parse(htmlSource == null ? "" : htmlSource);
    }
}
