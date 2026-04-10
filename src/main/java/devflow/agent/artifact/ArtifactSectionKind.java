package devflow.agent.artifact;

/**
 * 顶层 markdown 章节的结构化分类。
 *
 * <p>这里只表达“协议化章节类型”，不从自然语言里猜业务语义。
 * 这些章节名称属于我们自己维护的稳定文档结构，因此适合做确定性分类。
 */
public enum ArtifactSectionKind {
    OTHER,
    SOURCE_METADATA,
    CONTRACT_METADATA,
    CURRENT_NOTES
}
