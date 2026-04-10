package devflow.agent.editing;

import java.util.List;

/**
 * 统一解析精确代码改写载荷中的源码内容。
 * 优先使用 contentLines，避免模型在 JSON 字符串里直接放多行代码导致转义失败。
 */
public final class CodePatchContentResolver {

    private CodePatchContentResolver() {
    }

    public static String resolve(CodePreciseOperation operation) {
        if (operation == null) {
            return "";
        }
        List<String> contentLines = operation.contentLines();
        if (contentLines != null && !contentLines.isEmpty()) {
            return String.join("\n", contentLines);
        }
        return operation.content() == null ? "" : operation.content();
    }
}
