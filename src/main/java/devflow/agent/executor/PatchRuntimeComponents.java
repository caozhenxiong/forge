package devflow.agent.executor;

/**
 * patch 执行主链的共享运行时组件。
 *
 * <p>这一层只关心 patch 生成、apply、verify 与路由策略，不负责文件级上下文或兼容入口。
 */
record PatchRuntimeComponents(
        GeneratedContentGate generatedContentGate,
        HtmlFocusedRegionResolver htmlFocusedRegionResolver,
        WholeFilePatchExecutor wholeFilePatchExecutor,
        HostHtmlPatchExecutor hostHtmlPatchExecutor,
        EmbeddedPatchExecutor embeddedPatchExecutor,
        PreciseCodePatchExecutor preciseCodePatchExecutor,
        HtmlEditRoutingPolicy htmlEditRoutingPolicy,
        FileEditStrategyResolver fileEditStrategyResolver,
        PatchContextBuilder patchContextBuilder
) {
}
