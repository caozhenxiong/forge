package devflow.agent.executor;

/**
 * patch 请求共享的文本上下文。
 *
 * <p>当前 patch 主链需要的稳定文本只有两块：
 * 1. task package markdown；
 * 2. targeted context。
 *
 * <p>把它们抽成统一对象后，各类请求工厂只负责装配字段，不再重复拼接。
 */
record PatchRequestContext(
        String taskPackageMarkdown,
        String targetedContext
) {
}
