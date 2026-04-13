package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * patch 失败硬路由的集中设置。
 *
 * <p>当前先收口最关键的一个阈值：
 * 不可再拆分单元允许在本单元内重试几次。
 * 这样后续继续配置化时，不需要再回头挖 `PatchFailureRouter` 里的字面量。
 */
record PatchFailureRoutingSettings(
        int unsplittableUnitMaxAttempts
) {

    private static final String UNSPLITTABLE_UNIT_MAX_ATTEMPTS_KEY = "devflow.patch.unsplittable-max-attempts";

    PatchFailureRoutingSettings {
        unsplittableUnitMaxAttempts = Math.max(1, unsplittableUnitMaxAttempts);
    }

    static PatchFailureRoutingSettings defaults() {
        return new PatchFailureRoutingSettings(
                readPositiveInt(UNSPLITTABLE_UNIT_MAX_ATTEMPTS_KEY, 2)
        );
    }

    private static int readPositiveInt(String key, int fallback) {
        Integer configured = Integer.getInteger(key);
        return configured == null || configured <= 0 ? fallback : configured;
    }
}
