package devflow.agent.editing;

import java.util.List;

/**
 * 单文件结构化 diff hunk。
 *
 * <p>sourceStartLine 使用 1-based 行号，before/after 都按“纯行内容”表达，
 * 不携带统一 diff 的 `+/-/@@` 标记，便于模型稳定输出与本地修复。
 */
public record StructuredDiffHunk(
        Integer sourceStartLine,
        List<String> beforeLines,
        List<String> afterLines
) {
    public StructuredDiffHunk {
        beforeLines = beforeLines == null ? List.of() : List.copyOf(beforeLines);
        afterLines = afterLines == null ? List.of() : List.copyOf(afterLines);
    }

    public boolean emptyChange() {
        return beforeLines.equals(afterLines);
    }
}
