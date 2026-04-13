package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureType;

import java.nio.file.Path;

/**
 * 统一渲染宿主 HTML 精确改写的失败反馈。
 *
 * <p>HTML 宿主 patch 仍然走单独协议，但它的 retry/diagnosis 提示也应该和代码 patch 一样
 * 从上层编排中抽离，避免旧文件级编辑链继续堆积流程文案。
 */
final class HtmlPatchFeedbackRenderer {

    private HtmlPatchFeedbackRenderer() {
    }

    static String validationRetryFeedback(int attempt, Path relativePath, String validationFailure) {
        return """
                - attempt: %d
                - 文件: %s
                - 问题: %s
                要求：
                1. 继续使用 JSON exact replace 格式
                2. 只返回最小替换
                3. 如需补入口接线，应围绕现有 HTML 锚点做最小替换
                4. 保证改写后 HTML、脚本和样式都可解析
                """.formatted(attempt, relativePath, validationFailure);
    }

    static String focusedRegionValidationRetryFeedback(
            int attempt,
            Path relativePath,
            HtmlEditRegion region,
            String validationFailure
    ) {
        return """
                - attempt: %d
                - 文件: %s
                - 聚焦区块: %s
                - 问题: %s
                要求：
                1. 只返回当前聚焦区块的 exact replace JSON
                2. 不要输出其他区块内容
                3. 保证改写后 HTML/脚本/样式整体可解析
                """.formatted(attempt, relativePath, region, validationFailure);
    }

    static String generationFailureAdvice(GenerationFailureType failureType) {
        return isPatchLikeFailure(failureType)
                ? "请只返回合法 JSON，并提供 targetPath、baseContentHash、oldText、newText、replaceAll。"
                : "请继续使用 JSON exact replace 格式，只修改必要区块，并确保改写后的 HTML/脚本/样式都可解析。";
    }

    static String focusedRegionFailureAdvice() {
        return "请只改写当前聚焦区块，返回最小 exact replace，避免再次回到整页或整脚本的大块输出。";
    }

    private static boolean isPatchLikeFailure(GenerationFailureType failureType) {
        return failureType == GenerationFailureType.MODEL_OUTPUT_INVALID
                || failureType == GenerationFailureType.SNAPSHOT_STALE
                || failureType == GenerationFailureType.TARGET_SCOPE_VIOLATION
                || failureType == GenerationFailureType.TARGET_NOT_FOUND;
    }
}
