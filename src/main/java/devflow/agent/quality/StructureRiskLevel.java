package devflow.agent.quality;

/**
 * 结构风险等级。
 */
public enum StructureRiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public boolean atLeast(StructureRiskLevel other) {
        if (other == null) {
            return true;
        }
        return ordinal() >= other.ordinal();
    }
}
