package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 统一渲染代码 patch 的重试/终止反馈。
 *
 * <p>Claude Code 风格的 patch-first 主链里，失败反馈本身也是协议的一部分。
 * 这里把“哪类失败应该如何提示下一轮”从协调器里抽出来，避免门面类继续堆积大段字面量。
 */
final class CodePatchFeedbackRenderer {

    private CodePatchFeedbackRenderer() {
    }

    static String validationRetryFeedback(
            int attempt,
            Path relativePath,
            EditUnit unit,
            PatchFailure patchFailure
    ) {
        String validationFailure = patchFailure.evidence().isBlank() ? "本地内容校验失败" : patchFailure.evidence();
        return """
                - attempt: %d
                - 文件: %s
                - 编辑单元: %s
                - 问题: %s
                要求：
                1. 继续使用 JSON exact replace 格式
                2. 只改当前编辑单元允许的符号，避免整文件重写
                3. 产物必须保持语法和结构可解析
                4. %s
                """.formatted(
                attempt,
                relativePath,
                unit.label(),
                validationFailure,
                patchFailure.recommendedNextActionOr("继续保持当前 exact replace 目标与本地可验证结构一致。")
        );
    }

    static String abortFeedback(Path relativePath, EditUnit unit) {
        return """
                - 来源文件: %s
                - 编辑单元: %s
                - 问题: 当前单元输出被截断
                要求：
                1. 不要继续重试同一个过大单元
                2. 由外层把当前单元拆成更小的符号批次后再继续
                """.formatted(relativePath, unit.label());
    }

    static String truncationRetryFeedback(int attempt, Path relativePath, EditUnit unit) {
        return """
                - attempt: %d
                - 文件: %s
                - 编辑单元: %s
                - 问题: 当前单元输出被截断，但已无法继续按符号拆分
                要求：
                1. 继续只返回 JSON exact replace 对象
                2. 当前单元只能补当前符号体内的最小逻辑，不要新增远端辅助符号
                3. 不要通过整文件替换伪装成局部编辑
                4. 不要回到整文件重写
                """.formatted(attempt, relativePath, unit.label());
    }

    static String invalidJsonFeedback(int attempt, Path relativePath, EditUnit unit) {
        return """
                - attempt: %d
                - 文件: %s
                - 编辑单元: %s
                - 问题: 精确改写 JSON 非法
                要求：
                1. 继续只返回 JSON 对象
                2. 必须包含 targetPath、baseContentHash、oldText、newText、replaceAll
                3. oldText 必须直接从当前文件内容中原样拷贝
                4. 不要把摘要、解释或 Markdown 混进 JSON
                """.formatted(attempt, relativePath, unit.label());
    }

    static String patchSchemaFeedback(int attempt, Path relativePath, EditUnit unit) {
        String strictSingleSymbolAdvice = strictSingleSymbolAdvice(unit);
        return """
                - attempt: %d
                - 文件: %s
                - 编辑单元: %s
                - 问题: 当前 patch 协议不符合单元约束
                要求：
                1. 继续只返回 JSON 对象
                2. 当前单符号单元只能返回 1 个最小 exact replace
                3. oldText 只能覆盖目标符号对应的现有实现区域，不要扩到文件尾或别的符号
                4. 不要顺手改其他符号，也不要把整段文件重写成大替换
                5. %s
                """.formatted(attempt, relativePath, unit.label(), strictSingleSymbolAdvice);
    }

    static String scopeViolationFeedback(int attempt, Path relativePath, EditUnit unit) {
        String strictSingleSymbolAdvice = strictSingleSymbolAdvice(unit);
        return """
                - attempt: %d
                - 文件: %s
                - 编辑单元: %s
                - 问题: 当前 patch 越过了 edit unit 的边界
                要求：
                1. 只修改当前 allowedSymbols 列表中的符号
                2. 不要把 oldText 扩到当前单元以外
                3. 不要同时改写当前单元以外的符号
                4. %s
                """.formatted(attempt, relativePath, unit.label(), strictSingleSymbolAdvice);
    }

    static String symbolNotFoundFeedback(int attempt, Path relativePath, EditUnit unit, String evidence) {
        return """
                - attempt: %d
                - 文件: %s
                - 编辑单元: %s
                - 问题: %s
                要求：
                1. 当前文件只处理与本编辑单元符号清单匹配的问题
                2. 如果上一轮反馈主要指向其他文件，请忽略那些符号并只保留当前文件相关改动
                """.formatted(attempt, relativePath, unit.label(), evidence);
    }

    static String generationFailureAdvice(GenerationFailureType failureType) {
        return isPatchLikeFailure(failureType)
                ? "请只返回合法 JSON；必须提供 targetPath、baseContentHash、oldText、newText、replaceAll，且 oldText 范围必须留在当前编辑单元。"
                : "请继续使用 JSON exact replace 格式，只修改当前编辑单元的必要符号并保持语法可解析。";
    }

    private static boolean isPatchLikeFailure(GenerationFailureType failureType) {
        return failureType == GenerationFailureType.INVALID_PATCH_JSON
                || failureType == GenerationFailureType.PATCH_SCHEMA_INVALID
                || failureType == GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION
                || failureType == GenerationFailureType.SYMBOL_NOT_FOUND;
    }

    private static String strictSingleSymbolAdvice(EditUnit unit) {
        if (unit == null || !unit.restrictsSymbols() || unit.allowedSymbols().size() != 1) {
            return "如果当前单元已缩小，请继续保持目标符号与 allowedSymbols 一致。";
        }
        return "当前单元唯一允许的目标符号是 %s；只允许围绕它输出最小 exact replace。"
                .formatted(unit.allowedSymbols().getFirst());
    }
}
