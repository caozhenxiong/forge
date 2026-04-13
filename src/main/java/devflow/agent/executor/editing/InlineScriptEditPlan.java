package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * HTML 内联脚本的结构化编辑计划。
 *
 * <p>它把“脚本工作集”和“该工作集对应的 patch 计划”绑定到一起，
 * 让协调器不再直接关心内联脚本如何解析、如何切分单元。
 */
public record InlineScriptEditPlan(
        InlineScriptWorkingSet workingSet,
        PatchPlan patchPlan
) implements EmbeddingEditPlan {

    @Override
    public String embeddedContent() {
        return workingSet == null ? "" : workingSet.scriptContent();
    }

    @Override
    public boolean isUsable() {
        return workingSet != null && workingSet.isUsable() && patchPlan != null;
    }
}
