package devflow.agent.parsing;

import org.treesitter.TSNode;

/**
 * HTML 可编辑区域扫描器。
 *
 * <p>负责 head/body/app-root/app-style/app-script 等稳定编辑区间，
 * 供宿主 patch 和嵌入 patch 共用。
 */
final class TreeSitterEditableHtmlScanner {

    private final TreeSitterHtmlTagSupport tagSupport;

    TreeSitterEditableHtmlScanner(TreeSitterHtmlTagSupport tagSupport) {
        this.tagSupport = tagSupport;
    }

    HtmlEditableStructure scan(TreeSitterParserSupport parserSupport, String source) {
        TreeSitterParseMetrics metrics = parserSupport.parseMetrics(SourceLanguage.HTML, source);
        EditableHtmlFlags flags = new EditableHtmlFlags();
        TreeSitterNodeSupport.walk(metrics.rootNode(), node -> inspectEditableHtmlNode(source, node, flags));
        TreeSitterParseSummary summary = parserSupport.analyze(SourceLanguage.HTML, source);
        return new HtmlEditableStructure(
                summary,
                flags.headInnerRange,
                flags.bodyInnerRange,
                flags.appRootInnerRange,
                flags.appStyleInnerRange,
                flags.appScriptInnerRange
        );
    }

    private void inspectEditableHtmlNode(String source, TSNode node, EditableHtmlFlags flags) {
        String type = node.getType();
        if ("element".equals(type)) {
            TSNode startTag = TreeSitterNodeSupport.findChild(node, "start_tag");
            TSNode endTag = TreeSitterNodeSupport.findLastChild(node, "end_tag");
            if (startTag == null || endTag == null) {
                return;
            }
            String tagSource = TreeSitterNodeSupport.sliceUtf8(source, startTag.getStartByte(), startTag.getEndByte());
            String tagName = tagSupport.extractTagName(tagSource).toLowerCase();
            String id = tagSupport.extractAttribute(tagSource, "id");
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
            if (TreeSitterSupport.APP_ROOT_ID.equals(id) && flags.appRootInnerRange == null) {
                flags.appRootInnerRange = innerRange;
            }
            return;
        }
        if ("style_element".equals(type) || "script_element".equals(type)) {
            TSNode startTag = TreeSitterNodeSupport.findChild(node, "start_tag");
            TSNode endTag = TreeSitterNodeSupport.findLastChild(node, "end_tag");
            if (startTag == null || endTag == null) {
                return;
            }
            String tagSource = TreeSitterNodeSupport.sliceUtf8(source, startTag.getStartByte(), startTag.getEndByte());
            String id = tagSupport.extractAttribute(tagSource, "id");
            ByteRange innerRange = new ByteRange(startTag.getEndByte(), endTag.getStartByte());
            if (!innerRange.isValid()) {
                return;
            }
            if (TreeSitterSupport.APP_STYLE_ID.equals(id) && flags.appStyleInnerRange == null) {
                flags.appStyleInnerRange = innerRange;
            }
            if (TreeSitterSupport.APP_SCRIPT_ID.equals(id) && flags.appScriptInnerRange == null) {
                flags.appScriptInnerRange = innerRange;
            }
        }
    }

    private static final class EditableHtmlFlags {
        private ByteRange headInnerRange;
        private ByteRange bodyInnerRange;
        private ByteRange appRootInnerRange;
        private ByteRange appStyleInnerRange;
        private ByteRange appScriptInnerRange;
    }
}
