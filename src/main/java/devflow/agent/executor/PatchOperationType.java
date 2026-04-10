package devflow.agent.executor;

/**
 * 通用 patch 操作类型。
 *
 * <p>这里不直接暴露 JS/HTML 专用动作名，而是收成语言无关的最小操作集合，
 * 方便后续用轻量 adapter 把不同语言协议映射进统一内核。
 */
enum PatchOperationType {
    REPLACE_BLOCK,
    REPLACE_BODY,
    INSERT_INTO_BLOCK,
    APPEND_BLOCK
}
