package devflow.agent.parsing;

import java.util.LinkedHashSet;
import java.util.Set;
import org.treesitter.TSNode;

/**
 * HTML 静态结构扫描器。
 *
 * <p>负责 canvas/button/id/class 等宿主结构快照提取，
 * 供页面能力判断和最小运行表面分析复用。
 */
final class TreeSitterHtmlStructureScanner {

    private final TreeSitterHtmlTagSupport tagSupport;

    TreeSitterHtmlStructureScanner(TreeSitterHtmlTagSupport tagSupport) {
        this.tagSupport = tagSupport;
    }

    HtmlStructureSnapshot scan(TreeSitterParserSupport parserSupport, String source) {
        TreeSitterParseMetrics metrics = parserSupport.parseMetrics(SourceLanguage.HTML, source);
        Set<String> idSelectors = new LinkedHashSet<>();
        Set<String> classSelectors = new LinkedHashSet<>();
        Set<String> buttonSelectors = new LinkedHashSet<>();
        HtmlFlags flags = new HtmlFlags();
        TreeSitterNodeSupport.walk(
                metrics.rootNode(),
                node -> inspectHtmlNode(source, node, idSelectors, classSelectors, buttonSelectors, flags)
        );
        TreeSitterParseSummary summary = parserSupport.analyze(SourceLanguage.HTML, source);
        return new HtmlStructureSnapshot(
                summary,
                flags.hasHtmlRoot,
                flags.hasBody,
                flags.hasCanvas,
                flags.inlineStyleCount,
                flags.inlineScriptCount,
                Set.copyOf(idSelectors),
                Set.copyOf(classSelectors),
                Set.copyOf(buttonSelectors)
        );
    }

    private void inspectHtmlNode(
            String source,
            TSNode node,
            Set<String> idSelectors,
            Set<String> classSelectors,
            Set<String> buttonSelectors,
            HtmlFlags flags
    ) {
        String type = node.getType();
        if (!"start_tag".equals(type) && !"self_closing_tag".equals(type)) {
            return;
        }
        String tagSource = TreeSitterNodeSupport.sliceUtf8(source, node.getStartByte(), node.getEndByte());
        if (tagSource.isBlank()) {
            return;
        }
        String tagName = tagSupport.extractTagName(tagSource);
        if (tagName.isBlank()) {
            return;
        }
        String normalizedTag = tagName.toLowerCase();
        if ("html".equals(normalizedTag)) {
            flags.hasHtmlRoot = true;
        }
        if ("body".equals(normalizedTag)) {
            flags.hasBody = true;
        }
        if ("canvas".equals(normalizedTag)) {
            flags.hasCanvas = true;
        }
        if ("style".equals(normalizedTag)) {
            flags.inlineStyleCount++;
        }
        if ("script".equals(normalizedTag) && !tagSource.toLowerCase().contains("src=")) {
            flags.inlineScriptCount++;
        }

        String id = tagSupport.extractAttribute(tagSource, "id");
        if (!id.isBlank()) {
            idSelectors.add("#" + id);
        }

        String classes = tagSupport.extractAttribute(tagSource, "class");
        if (!classes.isBlank()) {
            for (String className : tagSupport.splitWhitespaceTokens(classes)) {
                if (!className.isBlank()) {
                    classSelectors.add("." + className);
                }
            }
        }
        if ("button".equals(normalizedTag)) {
            if (!id.isBlank()) {
                buttonSelectors.add("#" + id);
                return;
            }
            if (!classes.isBlank()) {
                for (String className : tagSupport.splitWhitespaceTokens(classes)) {
                    if (!className.isBlank()) {
                        buttonSelectors.add("." + className);
                    }
                }
                return;
            }
            buttonSelectors.add("button");
        }
    }

    private static final class HtmlFlags {
        private boolean hasHtmlRoot;
        private boolean hasBody;
        private boolean hasCanvas;
        private int inlineStyleCount;
        private int inlineScriptCount;
    }
}
