package devflow.agent.artifact;

/**
 * 文档 prompt 文本值辅助工具。
 *
 * <p>这里只保留非常轻量的值归一化逻辑，避免各阶段 prompt builder
 * 继续重复实现同样的空值处理。
 */
final class DocumentPromptValueSupport {

    private DocumentPromptValueSupport() {}

    static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
