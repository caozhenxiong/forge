package devflow.agent.parsing;

import org.treesitter.TSNode;

/**
 * tree-sitter 解析的底层统计结果。
 *
 * <p>这层只承载根节点和错误计数，避免把解析器内部细节继续塞回门面类。
 */
record TreeSitterParseMetrics(
        TSNode rootNode,
        int errorNodes,
        int missingNodes
) {
}
