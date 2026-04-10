package devflow.agent.artifact;

/**
 * 顶层章节块的稳定表示。
 *
 * <p>文档 merge / replace / sanitize 都基于这个结构处理，
 * 避免各处继续直接操作原始字符串偏移。
 */
record DocumentSectionBlock(int number, String title, int start, int end, String raw) {
}
