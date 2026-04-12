package devflow.agent.quality;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * capability id 的唯一规范化入口。
 *
 * <p>主链不再把业务 capability 写进核心枚举；这里统一承载：
 * 1. 内建技术 capability id；
 * 2. 外部 capability id 的规范化；
 * 3. QCAP 引用与 capability id 之间的稳定转换。
 */
public final class CapabilityIds {

    public static final String PAGE_LOAD = CapabilitySurface.PAGE_LOAD.wireValue();
    public static final String RUNTIME_STABILITY = CapabilitySurface.RUNTIME_STABILITY.wireValue();
    public static final String PRIMARY_VISUAL_SURFACE = CapabilitySurface.PRIMARY_VISUAL_SURFACE.wireValue();
    public static final String PRIMARY_INTERACTION = CapabilitySurface.PRIMARY_INTERACTION.wireValue();
    public static final String PERFORMANCE_LOAD = CapabilitySurface.PERFORMANCE_LOAD.wireValue();
    public static final String PERFORMANCE_INTERACTION = CapabilitySurface.PERFORMANCE_INTERACTION.wireValue();
    public static final String TIMED_STATE_PROGRESSION = "timed-state-progression";
    public static final String PAUSE_FREEZE = "pause-freeze";
    public static final String RESET_RESTORES_INITIAL_STATE = "reset-restores-initial-state";
    public static final String VISIBLE_PROGRESS_SIGNAL = "visible-progress-signal";

    private static final Pattern NON_REFERENCE_TOKEN = Pattern.compile("[^A-Z0-9]+");
    private static final Set<String> BUILTIN_IDS = Set.of(
            PAGE_LOAD,
            RUNTIME_STABILITY,
            PRIMARY_VISUAL_SURFACE,
            PRIMARY_INTERACTION,
            PERFORMANCE_LOAD,
            PERFORMANCE_INTERACTION
    );

    private CapabilityIds() {
    }

    public static String normalize(String capabilityId) {
        if (capabilityId == null) {
            return "";
        }
        String normalized = capabilityId.trim()
                .replace('\\', '-')
                .replace('_', '-')
                .replace(' ', '-')
                .toLowerCase(Locale.ROOT);
        while (normalized.contains("--")) {
            normalized = normalized.replace("--", "-");
        }
        return normalized;
    }

    public static List<String> normalizeList(Collection<String> capabilityIds) {
        if (capabilityIds == null || capabilityIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String capabilityId : capabilityIds) {
            String value = normalize(capabilityId);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    public static Set<String> normalizeSet(Collection<String> capabilityIds) {
        return Set.copyOf(normalizeList(capabilityIds));
    }

    public static boolean isBuiltin(String capabilityId) {
        return BUILTIN_IDS.contains(normalize(capabilityId));
    }

    public static boolean isBuiltinObservationTarget(String capabilityId) {
        String normalized = normalize(capabilityId);
        return PRIMARY_VISUAL_SURFACE.equals(normalized) || PRIMARY_INTERACTION.equals(normalized);
    }

    public static String builtinCatalog() {
        return String.join("|", BUILTIN_IDS.stream().sorted().toList());
    }

    public static String toReferenceSuffix(String capabilityId) {
        String normalized = normalize(capabilityId);
        if (normalized.isBlank()) {
            return "";
        }
        String upper = normalized.replace('-', '_').toUpperCase(Locale.ROOT);
        return NON_REFERENCE_TOKEN.matcher(upper).replaceAll("_");
    }

    public static String fromReferenceSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return "";
        }
        return normalize(suffix.replace('_', '-'));
    }
}
