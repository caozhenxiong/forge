package devflow.agent.quality;

import java.util.Set;

/**
 * 当前任务对覆盖面的显式意图。
 *
 * <p>能力项来自 contract / planner 产物，而不是从运行时 selector 文案猜测。
 */
public record CoverageIntent(
        Set<String> requiredCapabilityIds
) {

    public CoverageIntent {
        requiredCapabilityIds = requiredCapabilityIds == null ? Set.of() : CapabilityIds.normalizeSet(requiredCapabilityIds);
    }

    public static CoverageIntent empty() {
        return new CoverageIntent(Set.of());
    }
}
