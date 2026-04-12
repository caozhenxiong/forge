package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 统一渲染代码 patch 的重试/终止反馈。
 *
 * <p>Codex 风格的 patch-first 主链里，失败反馈本身也是协议的一部分。
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
                1. 继续使用 JSON 结构化 diff patch 格式
                2. 只改当前编辑单元允许的符号，避免整文件重写
                3. 产物必须保持语法和结构可解析
                4. %s
                """.formatted(
                attempt,
                relativePath,
                unit.label(),
                validationFailure,
                patchFailure.recommendedNextActionOr("继续保持当前 patch 目标与本地可验证结构一致。")
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
                1. 继续只返回 JSON 结构化 diff patch
                2. 当前单元只能补当前符号体内的最小逻辑，不要新增远端辅助符号
                3. 不要生成新的文件尾追加 hunk
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
                2. 必须包含 expectedSourceHash 和 hunks 数组
                3. 每个 hunk 都要提供 sourceStartLine、beforeLines、afterLines
                4. beforeLines/afterLines 必须逐行承载源码，不要把整段源码塞进单个字符串
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
                2. 当前单符号单元只能返回 1 个最小 hunk
                3. hunk 只能覆盖目标符号对应的现有实现区域，不要扩到文件尾或别的符号
                4. 不要顺手改其他符号，也不要把整段文件重写成大 hunk
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
                2. 不要生成新的文件尾追加 hunk
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
                ? "请只返回合法 JSON；必须提供 expectedSourceHash 与 hunks，beforeLines/afterLines 逐行承载源码，且 hunk 范围必须留在当前编辑单元。"
                : "请继续使用 JSON 结构化 diff patch 格式，只修改当前编辑单元的必要符号并保持语法可解析。";
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
        return "当前单元唯一允许的目标符号是 %s；只允许围绕它输出最小 body patch。"
                .formatted(unit.allowedSymbols().getFirst());
    }
}
