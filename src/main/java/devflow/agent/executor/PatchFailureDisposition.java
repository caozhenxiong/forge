package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * patch 单元失败后的确定性流转动作。
 *
 * <p>这层不讨论“业务上该不该继续”，只回答执行内核下一步怎么走：
 * 是继续重试当前单元、拆小当前单元，还是直接把失败上抛给外层阶段。
 */
enum PatchFailureDisposition {
    RETRY_CURRENT_UNIT,
    SPLIT_UNIT,
    ESCALATE
}
