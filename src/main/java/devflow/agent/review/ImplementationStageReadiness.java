package devflow.agent.review;

/**
 * implementation 阶段产物中关于“当前是否已准备好进入 review”的结构化结论。
 */
public record ImplementationStageReadiness(
        boolean stageReady,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems
) {

    public static ImplementationStageReadiness ready() {
        return new ImplementationStageReadiness(true, "", "", "", "");
    }

    public static ImplementationStageReadiness incomplete(String evidence) {
        String normalizedEvidence = evidence == null ? "" : evidence;
        return new ImplementationStageReadiness(
                false,
                "实现计划尚未执行完毕，当前仍处于阶段中间态。",
                "请继续完成未完成的 implementation 子任务，补齐骨架后的真实行为实现，再重新进入 implementation review。",
                normalizedEvidence,
                "1. 继续执行未完成的实现子任务。 2. 补齐当前阶段计划中的缺失能力。 3. 仅在所有计划子任务完成后再提交 implementation 审阅。"
        );
    }

    public static ImplementationStageReadiness architectCheckFailed(String evidence) {
        String normalizedEvidence = evidence == null ? "" : evidence;
        return new ImplementationStageReadiness(
                false,
                "实现计划已经执行完毕，但当前交付物仍未满足整体可运行契约。",
                "请优先修复入口接线、模块整合或整体可运行性问题，确保当前交付物满足 execution contract，再重新进入 implementation review。",
                normalizedEvidence,
                "1. 优先修复整体可运行性缺口，而不是重新规划完整 implementation backlog。 2. 检查入口文件、模块接线和初始化流程是否真正连通。 3. 补齐 architect 整体检查指出的关键行为缺口后再重新审阅。"
        );
    }
}
