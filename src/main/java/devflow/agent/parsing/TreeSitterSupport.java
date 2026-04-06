package devflow.agent.parsing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterHtml;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterJavascript;

@Component
public class TreeSitterSupport {

    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("^<\\s*([A-Za-z0-9:-]+)");
    private static final Pattern ID_PATTERN = Pattern.compile("\\bid\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public TreeSitterParseSummary analyze(Path relativePath, String source) {
        return analyze(SourceLanguage.fromPath(relativePath), source);
    }

    public TreeSitterParseSummary analyze(SourceLanguage language, String source) {
        if (language == SourceLanguage.UNSUPPORTED) {
            return new TreeSitterParseSummary(language, false, true, 0, 0);
        }
        ParseMetrics metrics = parseMetrics(language, source);
        return new TreeSitterParseSummary(
                language,
                true,
                metrics.errorNodes() == 0 && metrics.missingNodes() == 0,
                metrics.errorNodes(),
                metrics.missingNodes()
        );
    }

    public HtmlStructureSnapshot inspectHtml(String source) {
        ParseMetrics metrics = parseMetrics(SourceLanguage.HTML, source);
        Set<String> idSelectors = new LinkedHashSet<>();
        Set<String> buttonSelectors = new LinkedHashSet<>();
        HtmlFlags flags = new HtmlFlags();
        walk(metrics.rootNode(), node -> inspectHtmlNode(source, node, idSelectors, buttonSelectors, flags));
        TreeSitterParseSummary summary = new TreeSitterParseSummary(
                SourceLanguage.HTML,
                true,
                metrics.errorNodes() == 0 && metrics.missingNodes() == 0,
                metrics.errorNodes(),
                metrics.missingNodes()
        );
        return new HtmlStructureSnapshot(
                summary,
                flags.hasHtmlRoot,
                flags.hasBody,
                flags.hasCanvas,
                flags.inlineScriptCount,
                Set.copyOf(idSelectors),
                Set.copyOf(buttonSelectors)
        );
    }

    private void inspectHtmlNode(
            String source,
            TSNode node,
            Set<String> idSelectors,
            Set<String> buttonSelectors,
            HtmlFlags flags
    ) {
        String type = node.getType();
        if (!"start_tag".equals(type) && !"self_closing_tag".equals(type)) {
            return;
        }
        String tagSource = sliceUtf8(source, node.getStartByte(), node.getEndByte());
        if (tagSource.isBlank()) {
            return;
        }
        String tagName = extractTagName(tagSource);
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
        if ("script".equals(normalizedTag) && !tagSource.toLowerCase().contains("src=")) {
            flags.inlineScriptCount++;
        }

        String id = extractAttribute(tagSource, ID_PATTERN);
        if (!id.isBlank()) {
            idSelectors.add("#" + id);
        }

        String classes = extractAttribute(tagSource, CLASS_PATTERN);
        if ("button".equals(normalizedTag)) {
            if (!id.isBlank()) {
                buttonSelectors.add("#" + id);
                return;
            }
            if (!classes.isBlank()) {
                for (String className : classes.split("\\s+")) {
                    if (!className.isBlank()) {
                        buttonSelectors.add("." + className);
                    }
                }
                return;
            }
            buttonSelectors.add("button");
        }
    }

    private ParseMetrics parseMetrics(SourceLanguage language, String source) {
        TSParser parser = new TSParser();
        TSLanguage tsLanguage = createLanguage(language);
        if (!parser.setLanguage(tsLanguage)) {
            throw new IllegalStateException("Failed to set tree-sitter language: " + language);
        }
        TSTree tree = parser.parseString(null, source == null ? "" : source);
        TSNode root = tree.getRootNode();
        Counter counter = new Counter();
        walk(root, node -> {
            if (node.isError()) {
                counter.errorNodes++;
            }
            if (node.isMissing()) {
                counter.missingNodes++;
            }
        });
        if (counter.errorNodes == 0 && root.hasError()) {
            counter.errorNodes = 1;
        }
        return new ParseMetrics(root, counter.errorNodes, counter.missingNodes);
    }

    private void walk(TSNode node, java.util.function.Consumer<TSNode> consumer) {
        if (node == null || node.isNull()) {
            return;
        }
        consumer.accept(node);
        for (int index = 0; index < node.getChildCount(); index++) {
            walk(node.getChild(index), consumer);
        }
    }

    private TSLanguage createLanguage(SourceLanguage language) {
        return switch (language) {
            case HTML -> new TreeSitterHtml();
            case JAVASCRIPT -> new TreeSitterJavascript();
            case JAVA -> new TreeSitterJava();
            case UNSUPPORTED -> throw new IllegalArgumentException("Unsupported tree-sitter language");
        };
    }

    private String extractTagName(String tagSource) {
        Matcher matcher = TAG_NAME_PATTERN.matcher(tagSource);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractAttribute(String tagSource, Pattern pattern) {
        Matcher matcher = pattern.matcher(tagSource);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String sliceUtf8(String source, int startByte, int endByte) {
        byte[] bytes = (source == null ? "" : source).getBytes(StandardCharsets.UTF_8);
        int start = Math.max(0, Math.min(startByte, bytes.length));
        int end = Math.max(start, Math.min(endByte, bytes.length));
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    private record ParseMetrics(
            TSNode rootNode,
            int errorNodes,
            int missingNodes
    ) {
    }

    private static final class Counter {
        private int errorNodes;
        private int missingNodes;
    }

    private static final class HtmlFlags {
        private boolean hasHtmlRoot;
        private boolean hasBody;
        private boolean hasCanvas;
        private int inlineScriptCount;
    }
}
