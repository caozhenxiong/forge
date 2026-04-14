package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationBudgetProfile;

import devflow.agent.editing.precise.ExactReplaceEdit;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 内联脚本 patch 行为。
 *
 * <p>负责脚本类嵌入语言的 prompt 约束、apply 入口和预算差异，
 * 让宿主 patch 枚举只保留轻量路由职责。
 */
public final class InlineScriptEmbeddedPatchBehavior implements EmbeddedPatchBehavior {

    @Override
    public Path syntheticPath(Path relativePath) {
        return ProjectPathSupport.inlineScriptSyntheticPath(relativePath);
    }

    @Override
    public String baseSystemPrompt() {
        return """
                你是资深前端工程师。当前 HTML 入口文件里的主脚本已被抽成独立代码工作集。
                请只对这段内联脚本做基于当前状态的 exact replace 改写，不要重写整份 HTML，也不要返回 HTML。
                %s

                额外规则：
                1. 不要包裹 <script> 标签
                2. 当前编辑单元给出 allowedSymbols 时，只能修改这些现有符号对应区域
                3. 当前编辑单元没有 allowedSymbols 时，只能在脚本尾部追加最小辅助结构
                4. 当前宿主 HTML 的非脚本区域是只读的，%s
                """.formatted(
                ExactReplacePromptSupport.exactReplaceProtocol("脚本工作集"),
                immutableHostRegionInstruction()
        );
    }

    @Override
    public String describeTargets(PatchContextBuilder patchContextBuilder, Path relativePath, String currentContent) {
        return patchContextBuilder.describeInlineScriptTargets(relativePath, currentContent);
    }

    @Override
    public PatchApplyResult applyPatch(CodePatchKernel codePatchKernel, Path relativePath, String currentContent, ExactReplaceEdit edit) {
        return codePatchKernel.applyInlineScript(relativePath, currentContent, edit);
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
