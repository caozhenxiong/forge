package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * UI runtime contract 校验的稳定失败分类。
 *
 * <p>这层必须先区分“probe 自己无效”与“实现缺少可观察能力”，
 * 否则 TEST 会把工具链问题误打回到业务实现。
 */
public enum UiRuntimeContractValidationKind {
    VALID,
    PROBE_INVALID,
    ENTRY_OR_OWNER_INVALID,
    MISSING_REQUIRED_OBSERVATION_TARGET,
    SELECTOR_UNAVAILABLE
}
