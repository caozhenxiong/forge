package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

/**
 * 确定性 gate 失败后的推荐处置方式。
 *
 * <p>这里先只落地流程层最稳定的一批分类，后续 edit/test gate 可以继续复用同一套处置语义，
 * 避免每个执行器都自行发明“失败后该怎么办”的判断分支。
 */
public enum GateFailureDisposition {
    LOCAL_RETRYABLE,
    REPLAN_CURRENT_STAGE,
    ESCALATE,
    FATAL
}
