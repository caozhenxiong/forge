package devflow.agent.context;

import java.util.Locale;

/**
 * 绑定 contract 中的入口打包形态。
 *
 * <p>核心层只表达入口是否自包含，而不直接暴露 HTML/JS/CSS 这类交付表面词。
 */
public enum EntryPackagingMode {
    SELF_CONTAINED_ENTRY("self-contained-entry"),
    ENTRY_WITH_LOCAL_DEPENDENCIES("entry-with-local-dependencies"),
    NOT_APPLICABLE("not-applicable");

    private final String wireValue;

    EntryPackagingMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static EntryPackagingMode fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return NOT_APPLICABLE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (EntryPackagingMode mode : values()) {
            if (mode.wireValue.equals(normalized)) {
                return mode;
            }
        }
        return NOT_APPLICABLE;
    }
}
