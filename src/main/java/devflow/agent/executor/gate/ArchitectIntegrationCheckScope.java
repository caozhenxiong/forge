package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一标记 contract gate 在 implementation 内核中的作用域。
 *
 * <p>同一份 gate result 会被 runnable milestone、stage completion、snapshot、resume
 * 共同消费；scope 只说明这份结果是在哪个边界上做出的，不引入第二套判定逻辑。
 */
public enum ArchitectIntegrationCheckScope {
    RUNNABLE_MILESTONE,
    STAGE_COMPLETION
}
