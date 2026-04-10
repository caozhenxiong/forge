package devflow.agent.quality;

import devflow.agent.util.EnumParsers;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 用户可感知能力表面。
 *
 * <p>它描述的是“用户能感知到什么能力”，而不是具体实现细节或 testcase。
 * 后续 coverage / experience gate 都基于这层做判断。
 */
public enum CapabilitySurface {
    PAGE_LOAD("page-load", CapabilitySurfaceCategory.CORE_RUNTIME),
    RUNTIME_STABILITY("runtime-stability", CapabilitySurfaceCategory.CORE_RUNTIME),
    PRIMARY_VISUAL_SURFACE("primary-visual-surface", CapabilitySurfaceCategory.CORE_RUNTIME),
    PRIMARY_INTERACTION("primary-interaction", CapabilitySurfaceCategory.CORE_RUNTIME),
    PAUSE_FREEZE("pause-freeze", CapabilitySurfaceCategory.EXPERIENCE),
    RESET_RESTORES_INITIAL_STATE("reset-restores-initial-state", CapabilitySurfaceCategory.EXPERIENCE),
    TIMED_STATE_PROGRESSION("timed-state-progression", CapabilitySurfaceCategory.EXPERIENCE),
    VISIBLE_PROGRESS_SIGNAL("visible-progress-signal", CapabilitySurfaceCategory.EXPERIENCE),
    PERFORMANCE_LOAD("performance-load", CapabilitySurfaceCategory.PERFORMANCE),
    PERFORMANCE_INTERACTION("performance-interaction", CapabilitySurfaceCategory.PERFORMANCE);

    private final String wireValue;
    private final CapabilitySurfaceCategory category;

    CapabilitySurface(String wireValue, CapabilitySurfaceCategory category) {
        this.wireValue = wireValue;
        this.category = category;
    }

    public String wireValue() {
        return wireValue;
    }

    public CapabilitySurfaceCategory category() {
        return category;
    }

    public boolean isExperienceSurface() {
        return category == CapabilitySurfaceCategory.EXPERIENCE;
    }

    public static String wireCatalog() {
        return Arrays.stream(values())
                .map(CapabilitySurface::wireValue)
                .collect(Collectors.joining("|"));
    }

    public static CapabilitySurface fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        CapabilitySurface direct = EnumParsers.parseIgnoreCase(CapabilitySurface.class, value, null);
        if (direct != null) {
            return direct;
        }
        String normalized = value.trim().toLowerCase();
        for (CapabilitySurface surface : values()) {
            if (surface.wireValue.equals(normalized)) {
                return surface;
            }
        }
        return null;
    }
}
