package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 统一渲染整文件重写主链的失败反馈。
 */
final class WholeFileFeedbackRenderer {

    private WholeFileFeedbackRenderer() {
    }

    static String validationRetryFeedback(int attempt, Path relativePath, String validationFailure) {
        return """
                - attempt: %d
                - 文件: %s
                - 问题: %s
                要求：
                1. 重新输出完整文件
                2. 不要省略结尾
                3. 保证结构闭合、脚本可解析
                """.formatted(attempt, relativePath, validationFailure);
    }

    static String generationFailureAdvice() {
        return """
                请重新生成该文件，但把改单范围控制在当前子任务内，优先保留已存在的稳定结构。
                """;
    }
}
