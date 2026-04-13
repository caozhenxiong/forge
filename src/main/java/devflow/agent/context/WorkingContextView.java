package devflow.agent.context;

import devflow.agent.domain.StageType;

/**
 * 四层上下文中的 working 层。
 *
 * <p>这层只保留当前阶段当前轮真正要操作的上下文，
 * 用来避免把全量历史直接喂给 planner/coder/reviewer。
 */
public record WorkingContextView(
        StageType currentStage,
        String currentStageSummary,
        String workingSetSummary
) {
}
