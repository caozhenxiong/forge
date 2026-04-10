package devflow.agent.executor;

import java.util.List;

/**
 * 单文件扫描后的占位/空实现摘要。
 * 这样检查器可以先汇总，再决定如何转成最终 result。
 */
record ImplementationPlaceholderInspection(
        int placeholderMarkers,
        int emptyBehaviors,
        List<String> evidence,
        List<ImplementationCompletenessFinding> findings
) {
}
