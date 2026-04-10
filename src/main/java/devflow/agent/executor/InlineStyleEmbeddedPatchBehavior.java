package devflow.agent.executor;

import devflow.agent.editing.CodePrecisePatch;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 内联样式 patch 行为。
 *
 * <p>负责样式类嵌入语言的 prompt、apply 和预算差异，
 * 避免 `EmbeddedPatchKind` 继续长成大枚举。
 */
final class InlineStyleEmbeddedPatchBehavior implements EmbeddedPatchBehavior {

    @Override
    public Path syntheticPath(Path relativePath) {
        return ProjectPathSupport.inlineStyleSyntheticPath(relativePath);
    }

    @Override
    public String baseSystemPrompt() {
        return """
                你是资深前端工程师。当前 HTML 入口文件里的内联样式已被抽成独立样式工作集。
                请只对这段内联样式做规则级精确改写，不要重写整份 HTML，也不要返回整段样式。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "operations": [
                    {
                      "action": "REPLACE_SYMBOL|REPLACE_SYMBOL_BODY|INSERT_INTO_SYMBOL|APPEND_FILE",
                      "targetSymbol": "目标规则名或变量名；APPEND_FILE 时可为 null",
                      "targetKind": "rule|variable；APPEND_FILE 时可为 null",
                      "contentLines": ["逐行样式内容"]
                    }
                  ]
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. 不要返回 HTML，不要包裹 <style> 标签
                3. 必须优先使用 contentLines，不要在 content 字段里放多行源码字符串
                4. REPLACE_SYMBOL 必须提供完整规则或变量声明
                5. REPLACE_SYMBOL_BODY 只替换现有规则体内部内容；contentLines 只能写规则块内部的声明
                6. INSERT_INTO_SYMBOL 只在目标规则体内部插入内容
                7. APPEND_FILE 只用于新增顶层规则或变量定义
                8. 当前编辑单元给出 allowedSymbols 时，只能修改这些现有规则，不允许 APPEND_FILE
                9. 当前编辑单元没有 allowedSymbols 时，只能使用 APPEND_FILE 追加最小规则块
                10. 当前宿主 HTML 的非样式区域是只读的，%s
                """.formatted(immutableHostRegionInstruction());
    }

    @Override
    public String describeTargets(PatchContextBuilder patchContextBuilder, Path relativePath, String currentContent) {
        return patchContextBuilder.describeInlineStyleTargets(relativePath, currentContent);
    }

    @Override
    public PatchApplyResult applyPatch(CodePatchKernel codePatchKernel, Path relativePath, String currentContent, CodePrecisePatch patch) {
        return codePatchKernel.applyInlineStyle(relativePath, currentContent, patch);
    }

    @Override
    public String immutableHostRegionInstruction() {
        return "不要修改 script/markup";
    }

    @Override
    public String appendBudgetInstruction(EditUnit unit) {
        return """

                当前 append-only 单元预算：
                1. 本轮最多追加 %d 个新的样式规则或变量定义
                2. 优先补最小主题变量或最小规则块，不要一次铺满整套样式系统
                3. 如果需要更多规则，交给后续拆分后的 append-only 单元继续补
                """.formatted(unit.appendSymbolBudget());
    }

    @Override
    public String strictAppendOnlyInstruction() {
        return """

                当前 append-only 单元已缩到最小粒度：
                1. 只允许返回 1 个 operation
                2. 只允许使用 APPEND_FILE
                3. 只追加 1 个最小样式规则或变量定义
                4. 不要一次输出整套样式主题
                """;
    }

    @Override
    public String restrictedUnitInstruction() {
        return """

                当前编辑单元属于受限规则批次：
                1. 只能修改 allowedSymbols 中列出的规则
                2. 禁止 APPEND_FILE
                3. 如需新增规则，交给 append-only 单元处理
                """;
    }

    @Override
    public String truncationAppendGuidance() {
        return "如果当前单元是 append-only，只追加 1 个最小样式规则";
    }

    @Override
    public String truncationRestrictedGuidance() {
        return "如果当前单元是受限规则批次，只补当前规则块内的最小必要样式";
    }

    @Override
    public double defaultOutputBudgetRatio() {
        return GenerationBudgetProfile.focusedStyleOutputRatio();
    }

    @Override
    public int previewChars() {
        return GenerationBudgetProfile.inlineStylePreviewChars();
    }
}
