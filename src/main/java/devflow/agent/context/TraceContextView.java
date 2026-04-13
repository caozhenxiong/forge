package devflow.agent.context;

import devflow.agent.domain.StageType;

/**
 * 四层上下文中的 trace 层。
 *
 * <p>这层保留当前阶段相关的最近历史，供流程控制和升级仲裁使用。
 */
public record TraceContextView(
        StageType currentStage,
        String recentHistorySummary
) {
}
