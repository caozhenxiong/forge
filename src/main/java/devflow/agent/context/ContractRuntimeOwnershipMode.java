package devflow.agent.context;

import devflow.agent.executor.RuntimeOwnershipMode;
import java.util.Locale;

/**
 * 绑定 contract 中的主运行时所有权。
 *
 * <p>这层只回答“入口自己持有主运行时，还是 companion 持有主运行时”，
 * 不把 HTML 专属术语直接暴露给核心 contract。
 */
public enum ContractRuntimeOwnershipMode {
    ENTRY_OWNED("entry-owned"),
    COMPANION_OWNED("companion-owned"),
    NOT_APPLICABLE("not-applicable");

    private final String wireValue;

    ContractRuntimeOwnershipMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public RuntimeOwnershipMode toRuntimeOwnershipMode() {
        return switch (this) {
            case ENTRY_OWNED -> RuntimeOwnershipMode.INLINE_HOST;
            case COMPANION_OWNED -> RuntimeOwnershipMode.EXTERNAL_COMPANION;
            case NOT_APPLICABLE -> null;
        };
    }

    public static ContractRuntimeOwnershipMode fromRuntimeOwnershipMode(RuntimeOwnershipMode value) {
        if (value == null) {
            return NOT_APPLICABLE;
        }
        return switch (value) {
            case INLINE_HOST -> ENTRY_OWNED;
            case EXTERNAL_COMPANION -> COMPANION_OWNED;
        };
    }

    public static ContractRuntimeOwnershipMode fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return NOT_APPLICABLE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ContractRuntimeOwnershipMode mode : values()) {
            if (mode.wireValue.equals(normalized)) {
                return mode;
            }
        }
        return NOT_APPLICABLE;
    }
}
