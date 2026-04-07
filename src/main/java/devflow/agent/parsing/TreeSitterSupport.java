package devflow.agent.parsing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterGo;
import org.treesitter.TreeSitterHtml;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterPython;
import org.treesitter.TreeSitterTypescript;

@Component
public class TreeSitterSupport {

    public static final String APP_ROOT_ID = "app-root";
    public static final String APP_STYLE_ID = "app-style";
    public static final String APP_SCRIPT_ID = "app-script";

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

    public HtmlEditableStructure inspectEditableHtml(String source) {
        ParseMetrics metrics = parseMetrics(SourceLanguage.HTML, source);
        EditableHtmlFlags flags = new EditableHtmlFlags();
        walk(metrics.rootNode(), node -> inspectEditableHtmlNode(source, node, flags));
        TreeSitterParseSummary summary = new TreeSitterParseSummary(
                SourceLanguage.HTML,
                true,
                metrics.errorNodes() == 0 && metrics.missingNodes() == 0,
                metrics.errorNodes(),
                metrics.missingNodes()
        );
        return new HtmlEditableStructure(
                summary,
                flags.headInnerRange,
                flags.bodyInnerRange,
                flags.appRootInnerRange,
                flags.appStyleInnerRange,
                flags.appScriptInnerRange
        );
    }

    public CodeStructureSnapshot inspectCodeStructure(Path relativePath, String source) {
        return inspectCodeStructure(SourceLanguage.fromPath(relativePath), source);
    }

    public CodeStructureSnapshot inspectCodeStructure(SourceLanguage language, String source) {
        if (language != SourceLanguage.JAVASCRIPT
                && language != SourceLanguage.TYPESCRIPT
                && language != SourceLanguage.JAVA
                && language != SourceLanguage.PYTHON
                && language != SourceLanguage.GO) {
            return new CodeStructureSnapshot(
                    language,
                    new TreeSitterParseSummary(language, false, true, 0, 0),
                    List.of()
            );
        }
        ParseMetrics metrics = parseMetrics(language, source);
        TreeSitterParseSummary summary = new TreeSitterParseSummary(
                language,
                true,
                metrics.errorNodes() == 0 && metrics.missingNodes() == 0,
                metrics.errorNodes(),
                metrics.missingNodes()
        );
        List<CodeSymbol> symbols = switch (language) {
            case JAVASCRIPT, TYPESCRIPT -> inspectJavascriptSymbols(source, metrics.rootNode());
            case JAVA -> inspectJavaSymbols(source, metrics.rootNode());
            case PYTHON -> inspectPythonSymbols(source, metrics.rootNode());
            case GO -> inspectGoSymbols(source, metrics.rootNode());
            default -> List.of();
        };
        return new CodeStructureSnapshot(language, summary, symbols);
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

    private void inspectEditableHtmlNode(String source, TSNode node, EditableHtmlFlags flags) {
        String type = node.getType();
        if ("element".equals(type)) {
            TSNode startTag = findChild(node, "start_tag");
            TSNode endTag = findLastChild(node, "end_tag");
            if (startTag == null || endTag == null) {
                return;
            }
            String tagSource = sliceUtf8(source, startTag.getStartByte(), startTag.getEndByte());
            String tagName = extractTagName(tagSource).toLowerCase();
            String id = extractAttribute(tagSource, ID_PATTERN);
            ByteRange innerRange = new ByteRange(startTag.getEndByte(), endTag.getStartByte());
            if (!innerRange.isValid()) {
                return;
            }
            if ("head".equals(tagName) && flags.headInnerRange == null) {
                flags.headInnerRange = innerRange;
            }
            if ("body".equals(tagName) && flags.bodyInnerRange == null) {
                flags.bodyInnerRange = innerRange;
            }
            if (APP_ROOT_ID.equals(id) && flags.appRootInnerRange == null) {
                flags.appRootInnerRange = innerRange;
            }
            return;
        }
        if ("style_element".equals(type) || "script_element".equals(type)) {
            TSNode startTag = findChild(node, "start_tag");
            TSNode endTag = findLastChild(node, "end_tag");
            if (startTag == null || endTag == null) {
                return;
            }
            String tagSource = sliceUtf8(source, startTag.getStartByte(), startTag.getEndByte());
            String id = extractAttribute(tagSource, ID_PATTERN);
            ByteRange innerRange = new ByteRange(startTag.getEndByte(), endTag.getStartByte());
            if (!innerRange.isValid()) {
                return;
            }
            if (APP_STYLE_ID.equals(id) && flags.appStyleInnerRange == null) {
                flags.appStyleInnerRange = innerRange;
            }
            if (APP_SCRIPT_ID.equals(id) && flags.appScriptInnerRange == null) {
                flags.appScriptInnerRange = innerRange;
            }
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

    private TSNode findChild(TSNode parent, String type) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            TSNode child = parent.getChild(index);
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private TSNode findLastChild(TSNode parent, String type) {
        for (int index = parent.getChildCount() - 1; index >= 0; index--) {
            TSNode child = parent.getChild(index);
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private TSLanguage createLanguage(SourceLanguage language) {
        return switch (language) {
            case HTML -> new TreeSitterHtml();
            case JAVASCRIPT -> new TreeSitterJavascript();
            case TYPESCRIPT -> new TreeSitterTypescript();
            case JAVA -> new TreeSitterJava();
            case PYTHON -> new TreeSitterPython();
            case GO -> new TreeSitterGo();
            case UNSUPPORTED -> throw new IllegalArgumentException("Unsupported tree-sitter language");
        };
    }

    private List<CodeSymbol> inspectJavascriptSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        walk(root, node -> {
            switch (node.getType()) {
                case "class_declaration" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "class",
                        findChildByTypes(node, "identifier", "type_identifier"),
                        findChildByTypes(node, "class_body")
                ));
                case "method_definition" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "method",
                        findChildByTypes(node, "property_identifier", "identifier"),
                        findChildByTypes(node, "statement_block")
                ));
                case "function_declaration" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "function",
                        findChildByTypes(node, "identifier"),
                        findChildByTypes(node, "statement_block")
                ));
                case "lexical_declaration" -> addIfPresent(symbols, buildJavascriptVariableSymbol(source, node));
                default -> {
                }
            }
        });
        return deduplicateSymbols(symbols);
    }

    private List<CodeSymbol> inspectJavaSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        walk(root, node -> {
            switch (node.getType()) {
                case "class_declaration" -> addIfPresent(symbols, buildJavaTypeSymbol(source, node, "class"));
                case "interface_declaration" -> addIfPresent(symbols, buildJavaTypeSymbol(source, node, "interface"));
                case "enum_declaration" -> addIfPresent(symbols, buildJavaTypeSymbol(source, node, "enum"));
                case "record_declaration" -> addIfPresent(symbols, buildJavaTypeSymbol(source, node, "record"));
                case "constructor_declaration" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "constructor",
                        findChildByTypes(node, "identifier"),
                        findChildByTypes(node, "constructor_body")
                ));
                case "method_declaration" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "method",
                        findChildByTypes(node, "identifier"),
                        findChildByTypes(node, "block")
                ));
                default -> {
                }
            }
        });
        return deduplicateSymbols(symbols);
    }

    private List<CodeSymbol> inspectPythonSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        walk(root, node -> {
            switch (node.getType()) {
                case "class_definition" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "class",
                        findChildByTypes(node, "identifier"),
                        findChildByTypes(node, "block")
                ));
                case "function_definition" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "function",
                        findChildByTypes(node, "identifier"),
                        findChildByTypes(node, "block")
                ));
                case "decorated_definition" -> addIfPresent(symbols, buildDecoratedPythonSymbol(source, node));
                default -> {
                }
            }
        });
        return deduplicateSymbols(symbols);
    }

    private List<CodeSymbol> inspectGoSymbols(String source, TSNode root) {
        List<CodeSymbol> symbols = new ArrayList<>();
        walk(root, node -> {
            switch (node.getType()) {
                case "type_declaration" -> addIfPresent(symbols, buildGoTypeSymbol(source, node));
                case "method_declaration" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "method",
                        findChildByTypes(node, "field_identifier"),
                        findChildByTypes(node, "block")
                ));
                case "function_declaration" -> addIfPresent(symbols, buildNamedSymbol(
                        source,
                        node,
                        "function",
                        findChildByTypes(node, "identifier"),
                        findChildByTypes(node, "block")
                ));
                default -> {
                }
            }
        });
        return deduplicateSymbols(symbols);
    }

    private CodeSymbol buildJavaTypeSymbol(String source, TSNode node, String kind) {
        return buildNamedSymbol(
                source,
                node,
                kind,
                findChildByTypes(node, "identifier", "type_identifier"),
                findChildByTypes(node, "class_body", "interface_body", "enum_body", "record_body")
        );
    }

    private CodeSymbol buildDecoratedPythonSymbol(String source, TSNode node) {
        TSNode wrapped = findChildByTypes(node, "class_definition", "function_definition");
        if (wrapped == null) {
            return null;
        }
        String kind = "class_definition".equals(wrapped.getType()) ? "class" : "function";
        TSNode nameNode = findChildByTypes(wrapped, "identifier");
        TSNode bodyNode = findChildByTypes(wrapped, "block");
        if (nameNode == null || bodyNode == null) {
            return null;
        }
        String name = sliceSimpleName(source, nameNode);
        if (name.isBlank()) {
            return null;
        }
        return new CodeSymbol(
                name,
                kind,
                new ByteRange(node.getStartByte(), node.getEndByte()),
                innerBodyRange(bodyNode)
        );
    }

    private CodeSymbol buildGoTypeSymbol(String source, TSNode node) {
        TSNode typeSpec = findChildByTypes(node, "type_spec");
        if (typeSpec == null) {
            return null;
        }
        TSNode nameNode = findChildByTypes(typeSpec, "type_identifier", "identifier");
        TSNode typeBody = findChildByTypes(typeSpec, "struct_type", "interface_type");
        ByteRange bodyRange = null;
        if (typeBody != null) {
            bodyRange = innerBraceRange(typeBody);
            if (bodyRange == null) {
                TSNode nestedBraceContainer = findChildByTypes(typeBody, "field_declaration_list", "method_spec_list");
                bodyRange = innerBraceRange(nestedBraceContainer);
            }
        }
        String name = sliceSimpleName(source, nameNode);
        if (name.isBlank()) {
            return null;
        }
        return new CodeSymbol(
                name,
                "type",
                new ByteRange(node.getStartByte(), node.getEndByte()),
                bodyRange
        );
    }

    private CodeSymbol buildJavascriptVariableSymbol(String source, TSNode node) {
        TSNode declarator = findChildByTypes(node, "variable_declarator");
        if (declarator == null) {
            return null;
        }
        TSNode nameNode = findChildByTypes(declarator, "identifier");
        if (nameNode == null) {
            return null;
        }
        String name = sliceSimpleName(source, nameNode);
        if (name.isBlank()) {
            return null;
        }
        return new CodeSymbol(
                name,
                "variable",
                new ByteRange(node.getStartByte(), node.getEndByte()),
                null
        );
    }

    private CodeSymbol buildNamedSymbol(String source, TSNode node, String kind, TSNode nameNode, TSNode bodyNode) {
        if (node == null || nameNode == null) {
            return null;
        }
        String name = sliceSimpleName(source, nameNode);
        if (name.isBlank()) {
            return null;
        }
        ByteRange bodyRange = null;
        if (bodyNode != null) {
            bodyRange = innerBraceRange(bodyNode);
            if (bodyRange == null) {
                bodyRange = innerBodyRange(bodyNode);
            }
        }
        return new CodeSymbol(
                name,
                kind,
                new ByteRange(node.getStartByte(), node.getEndByte()),
                bodyRange
        );
    }

    private List<CodeSymbol> deduplicateSymbols(List<CodeSymbol> symbols) {
        List<CodeSymbol> deduplicated = new ArrayList<>();
        for (CodeSymbol candidate : symbols) {
            int existingIndex = findEquivalentSymbolIndex(deduplicated, candidate);
            if (existingIndex < 0) {
                deduplicated.add(candidate);
                continue;
            }
            CodeSymbol existing = deduplicated.get(existingIndex);
            if (symbolSpan(candidate) > symbolSpan(existing)) {
                deduplicated.set(existingIndex, candidate);
            }
        }
        return List.copyOf(deduplicated);
    }

    private int findEquivalentSymbolIndex(List<CodeSymbol> symbols, CodeSymbol candidate) {
        for (int index = 0; index < symbols.size(); index++) {
            CodeSymbol existing = symbols.get(index);
            if (!existing.kind().equals(candidate.kind()) || !existing.name().equals(candidate.name())) {
                continue;
            }
            ByteRange existingBody = existing.bodyInnerRange();
            ByteRange candidateBody = candidate.bodyInnerRange();
            if ((existingBody == null && candidateBody == null)
                    || (existingBody != null && candidateBody != null
                    && existingBody.startByte() == candidateBody.startByte()
                    && existingBody.endByte() == candidateBody.endByte())) {
                return index;
            }
        }
        return -1;
    }

    private int symbolSpan(CodeSymbol symbol) {
        return symbol.replaceRange().endByte() - symbol.replaceRange().startByte();
    }

    private void addIfPresent(List<CodeSymbol> symbols, CodeSymbol symbol) {
        if (symbol != null) {
            symbols.add(symbol);
        }
    }

    private TSNode findChildByTypes(TSNode parent, String... types) {
        if (parent == null || parent.isNull()) {
            return null;
        }
        for (int index = 0; index < parent.getChildCount(); index++) {
            TSNode child = parent.getChild(index);
            for (String type : types) {
                if (type.equals(child.getType())) {
                    return child;
                }
            }
        }
        return null;
    }

    private String sliceSimpleName(String source, TSNode node) {
        return node == null ? "" : sliceUtf8(source, node.getStartByte(), node.getEndByte()).trim();
    }

    private ByteRange innerBraceRange(TSNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        TSNode openBrace = null;
        TSNode closeBrace = null;
        for (int index = 0; index < node.getChildCount(); index++) {
            TSNode child = node.getChild(index);
            if ("{".equals(child.getType()) && openBrace == null) {
                openBrace = child;
            }
            if ("}".equals(child.getType())) {
                closeBrace = child;
            }
        }
        if (openBrace != null && closeBrace != null) {
            ByteRange range = new ByteRange(openBrace.getEndByte(), closeBrace.getStartByte());
            return range.isValid() ? range : null;
        }
        return null;
    }

    private ByteRange innerBodyRange(TSNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        ByteRange range = new ByteRange(node.getStartByte(), node.getEndByte());
        return range.isValid() ? range : null;
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

    private static final class EditableHtmlFlags {
        private ByteRange headInnerRange;
        private ByteRange bodyInnerRange;
        private ByteRange appRootInnerRange;
        private ByteRange appStyleInnerRange;
        private ByteRange appScriptInnerRange;
    }
}
