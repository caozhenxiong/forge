package devflow.agent.executor;

import devflow.agent.editing.StructuredDiffPatch;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 内联脚本 patch 行为。
 *
 * <p>负责脚本类嵌入语言的 prompt 约束、apply 入口和预算差异，
 * 让宿主 patch 枚举只保留轻量路由职责。
 */
final class InlineScriptEmbeddedPatchBehavior implements EmbeddedPatchBehavior {

    @Override
    public Path syntheticPath(Path relativePath) {
        return ProjectPathSupport.inlineScriptSyntheticPath(relativePath);
    }

    @Override
    public String baseSystemPrompt() {
        return """
                你是资深前端工程师。当前 HTML 入口文件里的主脚本已被抽成独立代码工作集。
                请只对这段内联脚本做基于当前状态的结构化 diff 改写，不要重写整份 HTML，也不要返回整段脚本。
                这条链承接旧的“符号级精确改写”意图，但输出协议已经切到结构化 diff hunk。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "expectedSourceHash": "必须原样拷贝输入中的 sourceHash",
                  "hunks": [
                    {
                      "sourceStartLine": 1,
                      "beforeLines": ["原样脚本行；纯插入可为空数组"],
                      "afterLines": ["修改后的脚本行；纯删除可为空数组"]
                    }
                  ]
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. 不要返回 HTML，不要包裹 <script> 标签
                3. `expectedSourceHash` 必须与输入中的 sourceHash 完全一致
                4. `sourceStartLine` 必须严格对应输入里的脚本行号
                5. `beforeLines` 必须与当前脚本该位置逐行完全一致
                6. 不要返回整段脚本，只输出最小 hunk
                7. 当前编辑单元给出 allowedSymbols 时，只能修改这些现有符号对应区域
                8. 当前编辑单元没有 allowedSymbols 时，只能在脚本尾部追加最小辅助结构
                9. 当前宿主 HTML 的非脚本区域是只读的，%s
                """.formatted(immutableHostRegionInstruction());
    }

    @Override
    public String describeTargets(PatchContextBuilder patchContextBuilder, Path relativePath, String currentContent) {
        return patchContextBuilder.describeInlineScriptTargets(relativePath, currentContent);
    }

    @Override
    public PatchApplyResult applyPatch(CodePatchKernel codePatchKernel, Path relativePath, String currentContent, StructuredDiffPatch patch) {
        return codePatchKernel.applyInlineScript(relativePath, currentContent, patch);
    }

    @Override
    public String immutableHostRegionInstruction() {
        return "不要修改 style/markup";
    }

    @Override
    public String appendBudgetInstruction(EditUnit unit) {
        return """

                当前 append-only 单元预算：
                1. 本轮最多追加 %d 个新的顶层辅助块
                2. 优先追加最小状态容器或最小辅助函数，不要一次补完整游戏逻辑
                3. 如果需要更多辅助符号，交给后续拆分后的 append-only 单元继续补
                """.formatted(unit.appendSymbolBudget());
    }

    @Override
    public String strictAppendOnlyInstruction() {
        return """

                当前 append-only 单元已缩到最小粒度：
                1. 只允许追加 1 个最小顶层辅助块
                2. 不要在这个辅助块里塞完整游戏逻辑、渲染流程或事件系统
                """;
    }

    @Override
    public String restrictedUnitInstruction() {
        return """

                当前编辑单元属于受限符号批次：
                1. 只能修改 allowedSymbols 中列出的现有符号对应区域
                2. 如需新增 helper，交给 append-only 单元处理，不要在本单元偷带辅助符号
                """;
    }

    @Override
    public String truncationAppendGuidance() {
        return "如果当前单元是 append-only，只追加 1 个最小辅助块";
    }

    @Override
    public String truncationRestrictedGuidance() {
        return "如果当前单元是 orchestrator，只补当前符号体内的最小调度逻辑";
    }

    @Override
    public double defaultOutputBudgetRatio() {
        return GenerationBudgetProfile.inlineScriptUnitOutputRatio();
    }

    @Override
    public int previewChars() {
        return GenerationBudgetProfile.inlineScriptPreviewChars();
    }
}
