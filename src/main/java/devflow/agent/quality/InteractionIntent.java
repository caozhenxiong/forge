package devflow.agent.quality;

import java.util.Set;

/**
 * 当前任务对交互语义的显式意图。
 *
 * <p>这里只承载已经由上游契约或 planner 明确声明的能力面，不在通用层自行猜测。
 */
public record InteractionIntent(
        Set<CapabilitySurface> expectedSurfaces
) {

    public InteractionIntent {
        expectedSurfaces = expectedSurfaces == null ? Set.of() : Set.copyOf(expectedSurfaces);
    }

    public static InteractionIntent empty() {
        return new InteractionIntent(Set.of());
    }
}
