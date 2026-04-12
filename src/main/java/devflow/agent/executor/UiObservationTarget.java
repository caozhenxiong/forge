package devflow.agent.executor;

import devflow.agent.quality.CapabilityIds;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 单个能力表面在当前交付物上的唯一观测目标。
 */
public record UiObservationTarget(
        String capabilityId,
        String selector,
        UiObservationMode mode,
        List<String> ownerPaths,
        boolean required
) {

    public UiObservationTarget {
        capabilityId = CapabilityIds.normalize(capabilityId);
        selector = selector == null ? "" : selector.trim();
        mode = mode == null ? UiObservationMode.DOM_SIGNATURE : mode;
        ownerPaths = normalizeOwnerPaths(ownerPaths);
    }

    public boolean usable() {
        return !capabilityId.isBlank() && !selector.isBlank() && !ownerPaths.isEmpty();
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
