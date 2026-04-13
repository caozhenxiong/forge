package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationBudgetProfile;

import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.editing.HtmlPrecisePatch;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;

/**
 * HTML 内联脚本的嵌入适配器。
 *
 * <p>它负责三件事：
 * 1. 判断当前 HTML 是否具备可编辑的 `app-script` 锚点；
 * 2. 把宿主 HTML 中的脚本抽成独立工作集，并生成 patch 计划；
 * 3. 把更新后的脚本重新安全回填到宿主 HTML。
 *
 * <p>它不负责：
 * 1. 调模型生成 patch；
 * 2. 决定截断后的流程跳转；
 * 3. 判定阶段是否完成。
 */
public class HtmlInlineScriptEmbeddingAdapter implements EmbeddingAdapter<InlineScriptEditPlan> {

    private final HtmlInlineScriptWorkingSetResolver workingSetResolver;
    private final EditUnitPlanner editUnitPlanner;
    private final PatchUnitSizer patchUnitSizer;
    private final HtmlPreciseEditor htmlPreciseEditor;

    public HtmlInlineScriptEmbeddingAdapter(
            TreeSitterSupport treeSitterSupport,
            EditUnitPlanner editUnitPlanner,
            PatchUnitSizer patchUnitSizer,
            HtmlPreciseEditor htmlPreciseEditor
    ) {
        this.workingSetResolver = new HtmlInlineScriptWorkingSetResolver(treeSitterSupport, editUnitPlanner.targetLocator());
        this.editUnitPlanner = editUnitPlanner;
        this.patchUnitSizer = patchUnitSizer;
        this.htmlPreciseEditor = htmlPreciseEditor;
    }

    @Override
    public boolean supports(Path htmlPath, String htmlSource) {
        InlineScriptWorkingSet workingSet = workingSetResolver.resolve(htmlPath, htmlSource);
        return workingSet != null && workingSet.isUsable();
    }

    @Override
    public InlineScriptEditPlan buildEditPlan(Path htmlPath, String htmlSource) {
        InlineScriptWorkingSet workingSet = workingSetResolver.resolve(htmlPath, htmlSource);
        if (workingSet == null || !workingSet.isUsable()) {
            return null;
        }
        PatchPlan patchPlan = patchUnitSizer.resize(
                editUnitPlanner.planInlineScriptPatch(workingSet.syntheticPath(), workingSet.scriptContent()),
                GenerationBudgetProfile.inlineScriptUnitOutputRatio(),
                false
        );
        return new InlineScriptEditPlan(workingSet, patchPlan);
    }

    @Override
    public String mergeIntoHost(String existingHtml, String scriptContent) {
        return htmlPreciseEditor.applyPatch(existingHtml, new HtmlPrecisePatch(null, null, scriptContent, null, null));
    }
}
