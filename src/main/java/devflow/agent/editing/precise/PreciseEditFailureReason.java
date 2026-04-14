package devflow.agent.editing.precise;

/**
 * 精确编辑阶段的稳定失败原因。
 */
public enum PreciseEditFailureReason {
    SNAPSHOT_STALE,
    TARGET_NOT_FOUND,
    TARGET_NOT_UNIQUE,
    TARGET_NOT_ADDRESSABLE,
    NO_MATERIAL_CHANGE,
    MODEL_OUTPUT_INVALID,
    TARGET_SCOPE_VIOLATION
}
