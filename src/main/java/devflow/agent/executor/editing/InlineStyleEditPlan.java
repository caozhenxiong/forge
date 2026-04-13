package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * HTML 内联样式的结构化编辑计划。
 *
 * <p>它把“样式工作集”和“该工作集对应的 patch 计划”绑定到一起，
 * 让协调器后续接入样式嵌入路径时不再关心样式如何抽取、如何切分 patch 单元。
 */
public record InlineStyleEditPlan(
        InlineStyleWorkingSet workingSet,
        PatchPlan patchPlan
) implements EmbeddingEditPlan {

    @Override
    public String embeddedContent() {
        return workingSet == null ? "" : workingSet.styleContent();
    }

    @Override
    public boolean isUsable() {
        return workingSet != null && workingSet.isUsable() && patchPlan != null;
    }
}
