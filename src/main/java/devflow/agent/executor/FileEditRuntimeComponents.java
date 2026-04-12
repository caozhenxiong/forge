package devflow.agent.executor;

/**
 * `FileEditCoordinator` 运行时依赖的聚合视图。
 *
 * <p>协调器本身只应该关心：
 * 1. 生成内容校验；
 * 2. 文件级路由；
 * 3. 事务写盘；
 * 4. 文件级上下文与请求工厂。
 *
 * <p>把 patch 执行链的整棵依赖图收成组件聚合后，协调器不再继续承担构造期装配职责。
 */
record FileEditRuntimeComponents(
        GeneratedContentGate generatedContentGate,
        WholeFilePatchExecutor wholeFilePatchExecutor,
        HtmlFilePatchExecutor htmlFilePatchExecutor,
        CodeFilePatchExecutor codeFilePatchExecutor,
        FileScopedContextSupport fileScopedContextSupport,
        PatchRequestFactory patchRequestFactory,
        FilePatchRouteRequestFactory filePatchRouteRequestFactory
) {
}
