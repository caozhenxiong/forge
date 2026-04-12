package devflow.agent.executor;

import devflow.agent.editing.StructuredDiffPatch;
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
                请只对这段内联样式做基于当前状态的结构化 diff 改写，不要重写整份 HTML，也不要返回整段样式。
                这条链承接旧的“符号级精确改写”意图，但输出协议已经切到结构化 diff hunk。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "expectedSourceHash": "必须原样拷贝输入中的 sourceHash",
                  "hunks": [
                    {
                      "sourceStartLine": 1,
                      "beforeLines": ["原样样式行；纯插入可为空数组"],
                      "afterLines": ["修改后的样式行；纯删除可为空数组"]
                    }
                  ]
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. 不要返回 HTML，不要包裹 <style> 标签
                3. `expectedSourceHash` 必须与输入中的 sourceHash 完全一致
                4. `sourceStartLine` 必须严格对应输入里的样式行号
                5. `beforeLines` 必须与当前样式该位置逐行完全一致
                6. 不要返回整段样式，只输出最小 hunk
                7. 当前编辑单元给出 allowedSymbols 时，只能修改这些现有规则对应区域
                8. 当前编辑单元没有 allowedSymbols 时，只能在样式尾部追加最小规则块
                9. 当前宿主 HTML 的非样式区域是只读的，%s
                """.formatted(immutableHostRegionInstruction());
    }

    @Override
    public String describeTargets(PatchContextBuilder patchContextBuilder, Path relativePath, String currentContent) {
        return patchContextBuilder.describeInlineStyleTargets(relativePath, currentContent);
    }

    @Override
    public PatchApplyResult applyPatch(CodePatchKernel codePatchKernel, Path relativePath, String currentContent, StructuredDiffPatch patch) {
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
                1. 本轮最多追加 %d 个新的样式块
                2. 优先补最小主题变量或最小规则块，不要一次铺满整套样式系统
                3. 如果需要更多规则，交给后续拆分后的 append-only 单元继续补
                """.formatted(unit.appendSymbolBudget());
    }

    @Override
    public String strictAppendOnlyInstruction() {
        return """

                当前 append-only 单元已缩到最小粒度：
                1. 只追加 1 个最小样式块
                2. 不要一次输出整套样式主题
                """;
    }

    @Override
    public String restrictedUnitInstruction() {
        return """

                当前编辑单元属于受限规则批次：
                1. 只能修改 allowedSymbols 中列出的规则对应区域
                2. 如需新增规则，交给 append-only 单元处理
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
