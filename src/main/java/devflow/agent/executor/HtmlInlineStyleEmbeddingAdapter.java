package devflow.agent.executor;

import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.editing.HtmlPrecisePatch;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;

/**
 * HTML 内联样式的嵌入适配器。
 *
 * <p>它负责：
 * 1. 判断当前 HTML 是否具备可编辑的 `app-style` 锚点；
 * 2. 把宿主 HTML 中的样式抽成独立工作集并生成 patch 计划；
 * 3. 把更新后的样式重新安全回填到宿主 HTML。
 */
class HtmlInlineStyleEmbeddingAdapter implements EmbeddingAdapter<InlineStyleEditPlan> {

    private final HtmlInlineStyleWorkingSetResolver workingSetResolver;
    private final EditUnitPlanner editUnitPlanner;
    private final PatchUnitSizer patchUnitSizer;
    private final HtmlPreciseEditor htmlPreciseEditor;

    HtmlInlineStyleEmbeddingAdapter(
            TreeSitterSupport treeSitterSupport,
            EditUnitPlanner editUnitPlanner,
            PatchUnitSizer patchUnitSizer,
            HtmlPreciseEditor htmlPreciseEditor
    ) {
        this.workingSetResolver = new HtmlInlineStyleWorkingSetResolver(treeSitterSupport, editUnitPlanner.targetLocator());
        this.editUnitPlanner = editUnitPlanner;
        this.patchUnitSizer = patchUnitSizer;
        this.htmlPreciseEditor = htmlPreciseEditor;
    }

    @Override
    public boolean supports(Path hostPath, String hostSource) {
        InlineStyleWorkingSet workingSet = workingSetResolver.resolve(hostPath, hostSource);
        return workingSet != null && workingSet.supportsRuleLevelPatch();
    }

    @Override
    public InlineStyleEditPlan buildEditPlan(Path hostPath, String hostSource) {
        InlineStyleWorkingSet workingSet = workingSetResolver.resolve(hostPath, hostSource);
        if (workingSet == null || !workingSet.supportsRuleLevelPatch()) {
            return null;
        }
        PatchPlan patchPlan = patchUnitSizer.resize(
                editUnitPlanner.planCodePatch(workingSet.syntheticPath(), workingSet.styleContent()),
                GenerationBudgetProfile.focusedStyleOutputRatio(),
                true
        );
        return new InlineStyleEditPlan(workingSet, patchPlan);
    }

    @Override
    public String mergeIntoHost(String hostSource, String embeddedContent) {
        return htmlPreciseEditor.applyPatch(hostSource, new HtmlPrecisePatch(null, embeddedContent, null, null, null));
    }
}
