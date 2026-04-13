package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 编辑单元类型。
 *
 * <p>这里描述的是“当前一次模型生成实际被允许改动的最小单元”，
 * 而不是文件类型或 workflow 阶段。Phase 7 的核心就是先把文件级工作集继续拆成更小的 edit unit。
 */
enum EditUnitKind {
    CODE_SYMBOL_BATCH,
    INLINE_SCRIPT_SYMBOL_BATCH
}
