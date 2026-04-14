package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * patch 失败硬路由的集中设置。
 *
 * <p>当前先收口最关键的一个阈值：
 * 不可再拆分单元允许在本单元内重试几次。
 * 这样后续继续配置化时，不需要再回头挖 `PatchFailureRouter` 里的字面量。
 */
@ConfigurationProperties(prefix = "devflow.patch.routing")
public record PatchFailureRoutingSettings(
        int unsplittableUnitMaxAttempts
) {

    private static final int DEFAULT_UNSPLITTABLE_UNIT_MAX_ATTEMPTS = 2;

    public PatchFailureRoutingSettings() {
        this(DEFAULT_UNSPLITTABLE_UNIT_MAX_ATTEMPTS);
    }

    public static PatchFailureRoutingSettings defaults() {
        return new PatchFailureRoutingSettings();
    }

    public PatchFailureRoutingSettings {
        unsplittableUnitMaxAttempts = Math.max(DEFAULT_UNSPLITTABLE_UNIT_MAX_ATTEMPTS, unsplittableUnitMaxAttempts);
    }
}
