package devflow.agent.executor;

/**
 * implementation 完整性检查里使用的基础问题类型。
 * 这里只描述“问题是什么”，不决定是否阻塞当前子任务。
 */
enum ImplementationCompletenessFindingType {
    PLACEHOLDER_MARKER,
    EMPTY_BEHAVIOR
}
