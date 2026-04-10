package devflow.agent.parsing;

import org.treesitter.TSLanguage;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterCss;
import org.treesitter.TreeSitterGo;
import org.treesitter.TreeSitterHtml;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterPython;
import org.treesitter.TreeSitterTypescript;

/**
 * 统一承载 tree-sitter 解析器创建和 parse metrics 统计。
 *
 * <p>它负责：
 * 1. 按语言创建 tree-sitter parser；
 * 2. 解析源码并统计 error/missing node；
 * 3. 生成通用的 parse summary。
 *
 * <p>它不负责：
 * 1. HTML 结构语义；
 * 2. 代码符号提取；
 * 3. 编辑目标定位。
 */
final class TreeSitterParserSupport {

    TreeSitterParseSummary analyze(SourceLanguage language, String source) {
        if (language == SourceLanguage.UNSUPPORTED) {
            return new TreeSitterParseSummary(language, false, true, 0, 0);
        }
        TreeSitterParseMetrics metrics = parseMetrics(language, source);
        return new TreeSitterParseSummary(
                language,
                true,
                metrics.errorNodes() == 0 && metrics.missingNodes() == 0,
                metrics.errorNodes(),
                metrics.missingNodes()
        );
    }

    TreeSitterParseMetrics parseMetrics(SourceLanguage language, String source) {
        TSParser parser = new TSParser();
        if (!parser.setLanguage(createLanguage(language))) {
            throw new IllegalStateException("Failed to set tree-sitter language: " + language);
        }
        TSTree tree = parser.parseString(null, source == null ? "" : source);
        var root = tree.getRootNode();
        Counter counter = new Counter();
        TreeSitterNodeSupport.walk(root, node -> {
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
        return new TreeSitterParseMetrics(root, counter.errorNodes, counter.missingNodes);
    }

    private TSLanguage createLanguage(SourceLanguage language) {
        if (language == SourceLanguage.HTML) {
            return new TreeSitterHtml();
        }
        if (language == SourceLanguage.JAVASCRIPT) {
            return new TreeSitterJavascript();
        }
        if (language == SourceLanguage.TYPESCRIPT) {
            return new TreeSitterTypescript();
        }
        if (language == SourceLanguage.CSS) {
            return new TreeSitterCss();
        }
        if (language == SourceLanguage.JAVA) {
            return new TreeSitterJava();
        }
        if (language == SourceLanguage.PYTHON) {
            return new TreeSitterPython();
        }
        if (language == SourceLanguage.GO) {
            return new TreeSitterGo();
        }
        throw new IllegalArgumentException("Unsupported tree-sitter language");
    }

    private static final class Counter {
        private int errorNodes;
        private int missingNodes;
    }
}
