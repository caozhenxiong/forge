package devflow.agent.parsing;

import java.nio.charset.StandardCharsets;
import org.treesitter.TSNode;

/**
 * tree-sitter 语法树遍历和切片公共工具。
 *
 * <p>这层只做确定性的节点访问和 UTF-8 位置换算，不承载业务语义。
 */
final class TreeSitterNodeSupport {

    private TreeSitterNodeSupport() {
    }

    static void walk(TSNode node, java.util.function.Consumer<TSNode> consumer) {
        if (node == null || node.isNull()) {
            return;
        }
        consumer.accept(node);
        for (int index = 0; index < node.getChildCount(); index++) {
            walk(node.getChild(index), consumer);
        }
    }

    static TSNode findChild(TSNode parent, String type) {
        if (parent == null || parent.isNull()) {
            return null;
        }
        for (int index = 0; index < parent.getChildCount(); index++) {
            TSNode child = parent.getChild(index);
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    static TSNode findLastChild(TSNode parent, String type) {
        if (parent == null || parent.isNull()) {
            return null;
        }
        for (int index = parent.getChildCount() - 1; index >= 0; index--) {
            TSNode child = parent.getChild(index);
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    static TSNode findChildByTypes(TSNode parent, String... types) {
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

    static String sliceUtf8(String source, int startByte, int endByte) {
        byte[] bytes = (source == null ? "" : source).getBytes(StandardCharsets.UTF_8);
        int start = Math.max(0, Math.min(startByte, bytes.length));
        int end = Math.max(start, Math.min(endByte, bytes.length));
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    static String sliceSimpleName(String source, TSNode node) {
        return node == null ? "" : sliceUtf8(source, node.getStartByte(), node.getEndByte()).trim();
    }

    static ByteRange innerBraceRange(TSNode node) {
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

    static ByteRange innerBodyRange(TSNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        ByteRange range = new ByteRange(node.getStartByte(), node.getEndByte());
        return range.isValid() ? range : null;
    }

    static int toUtf8ByteOffset(String source, int charIndex) {
        int boundedIndex = Math.max(0, Math.min(charIndex, source == null ? 0 : source.length()));
        return (source == null ? "" : source.substring(0, boundedIndex)).getBytes(StandardCharsets.UTF_8).length;
    }
}
