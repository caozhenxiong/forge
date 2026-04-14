package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.precise.ExactReplaceEdit;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 宿主内嵌 patch 单元的种类。
 *
 * <p>这层集中收口脚本/样式这类嵌入语言在 patch 主链里的稳定差异：
 * 1. 统一策略标签；
 * 2. 统一 synthetic path；
 * 3. 统一目标摘要入口；
 * 4. 统一 apply + verify 入口。
 *
 * <p>这样宿主片段类型继续扩展时，不需要再复制一套脚本/样式分支。
 */
public enum EmbeddedPatchKind {
    SCRIPT(
            FileEditStrategyNames.INLINE_SCRIPT_WORKSET,
            "内联脚本",
            "脚本",
            "符号",
            new InlineScriptEmbeddedPatchBehavior()
    ),
    STYLE(
            FileEditStrategyNames.INLINE_STYLE_WORKSET,
            "内联样式",
            "样式",
            "规则",
            new InlineStyleEmbeddedPatchBehavior()
    );

    private final String strategyName;
    private final String displayName;
    private final String contentName;
    private final String targetName;
    private final EmbeddedPatchBehavior behavior;

    EmbeddedPatchKind(
            String strategyName,
            String displayName,
            String contentName,
            String targetName,
            EmbeddedPatchBehavior behavior
    ) {
        this.strategyName = strategyName;
        this.displayName = displayName;
        this.contentName = contentName;
        this.targetName = targetName;
        this.behavior = behavior;
    }

    public String strategyName() {
        return strategyName;
    }

    public String displayName() {
        return displayName;
    }

    public String contentName() {
        return contentName;
    }

    public String targetName() {
        return targetName;
    }

    public double defaultOutputBudgetRatio() {
        return behavior.defaultOutputBudgetRatio();
    }

    public int previewChars() {
        return behavior.previewChars();
    }

    public String operationName(Path relativePath, String unitLabel) {
        return FileEditStrategyNames.operation(strategyName, relativePath, unitLabel);
    }

    public String observerName(String unitLabel) {
        return FileEditStrategyNames.observer(strategyName, unitLabel);
    }

    public String baseSystemPrompt() {
        return behavior.baseSystemPrompt();
    }

    public Path syntheticPath(Path relativePath) {
        return behavior.syntheticPath(relativePath);
    }

    public String describeTargets(PatchContextBuilder patchContextBuilder, Path relativePath, String currentContent) {
        return behavior.describeTargets(patchContextBuilder, relativePath, currentContent);
    }

    public PatchApplyResult applyPatch(CodePatchKernel codePatchKernel, Path relativePath, String currentContent, ExactReplaceEdit edit) {
        return behavior.applyPatch(codePatchKernel, relativePath, currentContent, edit);
    }

    public String immutableHostRegionInstruction() {
        return behavior.immutableHostRegionInstruction();
    }

    public String appendBudgetInstruction(EditUnit unit) {
        return behavior.appendBudgetInstruction(unit);
    }

    public String strictAppendOnlyInstruction() {
        return behavior.strictAppendOnlyInstruction();
    }

    public String restrictedUnitInstruction() {
        return behavior.restrictedUnitInstruction();
    }

    public String truncationAppendGuidance() {
        return behavior.truncationAppendGuidance();
    }

    public String truncationRestrictedGuidance() {
        return behavior.truncationRestrictedGuidance();
    }
}
