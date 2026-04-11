package devflow.agent.executor;

import devflow.agent.quality.CapabilitySurface;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 单个能力表面在当前交付物上的唯一观测目标。
 */
public record UiObservationTarget(
        CapabilitySurface surface,
        String selector,
        UiObservationMode mode,
        List<String> ownerPaths,
        boolean required
) {

    public UiObservationTarget {
        selector = selector == null ? "" : selector.trim();
        mode = mode == null ? UiObservationMode.DOM_SIGNATURE : mode;
        ownerPaths = normalizeOwnerPaths(ownerPaths);
    }

    public boolean usable() {
        return surface != null && !selector.isBlank() && !ownerPaths.isEmpty();
    }

    private static List<String> normalizeOwnerPaths(List<String> ownerPaths) {
        if (ownerPaths == null || ownerPaths.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ownerPath : ownerPaths) {
            if (ownerPath == null || ownerPath.isBlank()) {
                continue;
            }
            normalized.add(ownerPath.trim().replace('\\', '/'));
        }
        return List.copyOf(normalized);
    }
}
