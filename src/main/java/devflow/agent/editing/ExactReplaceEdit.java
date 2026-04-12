package devflow.agent.editing;

/**
 * Claude Code 风格的精确替换事务。
 *
 * <p>模型只声明“它看到了哪份当前内容、要把哪段原文替换成什么”，
 * 真正的匹配、唯一性和状态校验都在宿主侧确定性执行。
 */
public record ExactReplaceEdit(
        String targetPath,
        String baseContentHash,
        String oldText,
        String newText,
        boolean replaceAll
) {

    public ExactReplaceEdit {
        targetPath = targetPath == null ? "" : targetPath.strip();
        baseContentHash = baseContentHash == null ? "" : baseContentHash.strip();
        oldText = oldText == null ? "" : oldText;
        newText = newText == null ? "" : newText;
    }
}
