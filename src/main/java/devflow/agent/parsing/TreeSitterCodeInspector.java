package devflow.agent.parsing;

import java.util.List;
import org.treesitter.TSNode;

/**
 * 代码结构与符号检查器。
 *
 * <p>它负责：
 * 1. 按语言提取稳定的符号列表；
 * 2. 为 precise edit / target locator 提供统一的结构快照。
 */
final class TreeSitterCodeInspector {

    private final TreeSitterParserSupport parserSupport;
    private final TreeSitterWebCodeInspector webCodeInspector;
    private final TreeSitterBackendCodeInspector backendCodeInspector;

    TreeSitterCodeInspector(TreeSitterParserSupport parserSupport) {
        this.parserSupport = parserSupport;
        TreeSitterSymbolSupport symbolSupport = new TreeSitterSymbolSupport();
        this.webCodeInspector = new TreeSitterWebCodeInspector(symbolSupport);
        this.backendCodeInspector = new TreeSitterBackendCodeInspector(symbolSupport);
    }

    CodeStructureSnapshot inspectCodeStructure(SourceLanguage language, String source) {
        if (language != SourceLanguage.JAVASCRIPT
                && language != SourceLanguage.TYPESCRIPT
                && language != SourceLanguage.CSS
                && language != SourceLanguage.JAVA
                && language != SourceLanguage.PYTHON
                && language != SourceLanguage.GO) {
            return new CodeStructureSnapshot(
                    language,
                    new TreeSitterParseSummary(language, false, true, 0, 0),
                    List.of()
            );
        }
        TreeSitterParseMetrics metrics = parserSupport.parseMetrics(language, source);
        TreeSitterParseSummary summary = parserSupport.analyze(language, source);
        List<CodeSymbol> symbols = summary.valid()
                ? inspectSymbols(language, source, metrics.rootNode())
                : List.of();
        return new CodeStructureSnapshot(language, summary, symbols);
    }

    private List<CodeSymbol> inspectSymbols(SourceLanguage language, String source, TSNode root) {
        if (language == SourceLanguage.JAVASCRIPT || language == SourceLanguage.TYPESCRIPT) {
            return webCodeInspector.inspectJavascriptSymbols(source, root);
        }
        if (language == SourceLanguage.JAVA) {
            return backendCodeInspector.inspectJavaSymbols(source, root);
        }
        if (language == SourceLanguage.PYTHON) {
            return backendCodeInspector.inspectPythonSymbols(source, root);
        }
        if (language == SourceLanguage.CSS) {
            return webCodeInspector.inspectCssSymbols(source, root);
        }
        if (language == SourceLanguage.GO) {
            return backendCodeInspector.inspectGoSymbols(source, root);
        }
        return List.of();
    }
}
