package devflow.agent.parsing;

/**
 * HTML 结构和可编辑区域检查器。
 *
 * <p>它负责：
 * 1. 从 HTML 中提取结构快照；
 * 2. 提取 head/body/app-root/app-style/app-script 等可编辑区间；
 * 3. 保持这些规则只留在 HTML 语义层。
 */
final class TreeSitterHtmlInspector {

    private final TreeSitterParserSupport parserSupport;
    private final TreeSitterHtmlStructureScanner structureScanner;
    private final TreeSitterEditableHtmlScanner editableHtmlScanner;

    TreeSitterHtmlInspector(TreeSitterParserSupport parserSupport) {
        this.parserSupport = parserSupport;
        TreeSitterHtmlTagSupport tagSupport = new TreeSitterHtmlTagSupport();
        this.structureScanner = new TreeSitterHtmlStructureScanner(tagSupport);
        this.editableHtmlScanner = new TreeSitterEditableHtmlScanner(tagSupport);
    }

    HtmlStructureSnapshot inspectHtml(String source) {
        return structureScanner.scan(parserSupport, source);
    }

    HtmlEditableStructure inspectEditableHtml(String source) {
        return editableHtmlScanner.scan(parserSupport, source);
    }
}
