package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 统一渲染宿主 HTML 精确改写的失败反馈。
 *
 * <p>HTML 宿主 patch 仍然走单独协议，但它的 retry/diagnosis 提示也应该和代码 patch 一样
 * 从协调器中抽离，避免 FileEditCoordinator 继续堆积流程文案。
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
                1. 继续使用 JSON 精确改写格式
                2. 只返回需要修改的区块
                3. 如需补入口接线，可使用 headAppendHtml / bodyAppendHtml 追加资源片段
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
                1. 只输出当前聚焦区块的完整内容
                2. 不要输出其他区块
                3. 保证改写后 HTML/脚本/样式整体可解析
                """.formatted(attempt, relativePath, region, validationFailure);
    }

    static String generationFailureAdvice(GenerationFailureType failureType) {
        return isPatchLikeFailure(failureType)
                ? "请只返回合法 JSON，并保持 markupHtml/styleCss/scriptJs/headAppendHtml/bodyAppendHtml 至少有一个非 null。"
                : "请继续使用 JSON 精确改写格式，只修改必要区块；如果需要接线外部资源，可使用 headAppendHtml/bodyAppendHtml，并确保改写后的 HTML/脚本/样式都可解析。";
    }

    static String focusedRegionFailureAdvice() {
        return "请只改写当前聚焦区块，避免再次回到整页或整脚本的大块输出。";
    }

    private static boolean isPatchLikeFailure(GenerationFailureType failureType) {
        return failureType == GenerationFailureType.INVALID_PATCH_JSON
                || failureType == GenerationFailureType.PATCH_SCHEMA_INVALID
                || failureType == GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION
                || failureType == GenerationFailureType.SYMBOL_NOT_FOUND;
    }
}
