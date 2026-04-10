package devflow.agent.executor;

import java.util.List;

/**
 * 表示 implementation 阶段的整体完成状态。
 * 它只回答“计划是否完成、阶段是否可继续推进”，不负责生成 markdown。
 */
record ImplementationStageStatus(
        int plannedSubtasks,
        int executedSubtasks,
        int completedSubtasks,
        boolean planCompleted,
        boolean architectCheckPassed,
        boolean stageReady,
        List<String> incompleteSubtasks
) {
}
