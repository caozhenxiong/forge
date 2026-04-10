package devflow.agent.executor;

/**
 * implementation 完整性检查中的单条发现。
 * symbolName 只用于责任域匹配，不直接决定问题等级。
 */
record ImplementationCompletenessFinding(
        ImplementationCompletenessFindingType type,
        String symbolName,
        String evidence
) {
}
