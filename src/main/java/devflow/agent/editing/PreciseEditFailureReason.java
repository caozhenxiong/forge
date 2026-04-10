package devflow.agent.editing;

/**
 * 精确编辑阶段的稳定失败原因。
 */
public enum PreciseEditFailureReason {
    ANCHOR_MISSING,
    PATCH_EMPTY,
    PATCH_SCHEMA_INVALID,
    EDIT_UNIT_SCOPE_VIOLATION,
    SYMBOL_NOT_FOUND
}
