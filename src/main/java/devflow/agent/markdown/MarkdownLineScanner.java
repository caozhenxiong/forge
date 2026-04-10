package devflow.agent.markdown;

/**
 * Markdown 逐行扫描支持。
 *
 * <p>统一处理带 offset 的逐行遍历，避免章节扫描器继续重复维护基础遍历逻辑。
 */
final class MarkdownLineScanner {

    void forEachLine(String content, MarkdownSectionScanner.LineConsumer consumer) {
        int start = 0;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '\n') {
                consumer.accept(content.substring(start, index), start, index);
                start = index + 1;
            }
        }
        if (start <= content.length()) {
            consumer.accept(content.substring(start), start, content.length());
        }
    }
}
