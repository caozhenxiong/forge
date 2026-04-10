package devflow.agent.quality;

/**
 * 能力覆盖期望等级。
 */
public enum CapabilityExpectation {
    REQUIRED,
    SMOKE,
    OPTIONAL;

    public boolean required() {
        return this == REQUIRED;
    }
}
