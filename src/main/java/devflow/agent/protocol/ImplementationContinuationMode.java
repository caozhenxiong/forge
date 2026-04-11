package devflow.agent.protocol;

/**
 * implementation 阶段在 stage artifact 中声明的下一步动作。
 *
 * <p>这层只表达阶段是否还能继续自动实现：
 * 1. `CONTINUE_SUBTASKS` 表示计划尚未完成或仍可在当前阶段内继续修补；
 * 2. `BLOCK_STAGE` 表示出现确定性工具/协议阻塞，必须停在当前阶段等待人工处理。
 */
public enum ImplementationContinuationMode {
    CONTINUE_SUBTASKS,
    BLOCK_STAGE
}
