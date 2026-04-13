package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

/**
 * implementation 完整性检查中的单条发现。
 * symbolName 只用于责任域匹配，不直接决定问题等级。
 */
public record ImplementationCompletenessFinding(
        ImplementationCompletenessFindingType type,
        String symbolName,
        String evidence
) {
}
