package devflow.agent.parsing;

public record TreeSitterParseSummary(
        SourceLanguage language,
        boolean supported,
        boolean valid,
        int errorNodes,
        int missingNodes
) {
    public String describe() {
        if (!supported) {
            return "tree-sitter 不支持该文件类型";
        }
        if (valid) {
            return "tree-sitter 解析通过";
        }
        return "tree-sitter 解析失败: errorNodes=%d, missingNodes=%d".formatted(errorNodes, missingNodes);
    }
}
