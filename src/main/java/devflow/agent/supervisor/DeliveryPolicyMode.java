package devflow.agent.supervisor;

import devflow.agent.executor.DeliveryMode;
import devflow.agent.util.EnumParsers;

/**
 * Supervisor / recovery 层使用的交付模式协议。
 *
 * <p>相比 implementation 内部的 DeliveryMode，这里额外保留 NONE，
 * 用来表达“当前动作不推动实现子任务交付策略”。
 */
public enum DeliveryPolicyMode {
    NONE("NONE"),
    SKELETON("SKELETON"),
    INCREMENTAL("INCREMENTAL"),
    PATCH("PATCH"),
    REWORK("REWORK");

    private final String wireValue;

    DeliveryPolicyMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public DeliveryMode toDeliveryModeOrNull() {
        return switch (this) {
            case SKELETON -> DeliveryMode.SKELETON;
            case INCREMENTAL -> DeliveryMode.INCREMENTAL;
            case PATCH -> DeliveryMode.PATCH;
            case REWORK -> DeliveryMode.REWORK;
            case NONE -> null;
        };
    }

    public static DeliveryPolicyMode fromWireValue(String value, DeliveryPolicyMode fallback) {
        return EnumParsers.parseIgnoreCase(DeliveryPolicyMode.class, value, fallback);
    }
}
