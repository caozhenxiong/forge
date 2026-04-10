package devflow.agent.executor;

import devflow.agent.project.FileProjectWorkspace;

/**
 * 组装文件级路由与上下文支撑。
 *
 * <p>这一层在 patch 主链之上补齐任务包作用域与请求工厂，但不再创建底层 patch 执行器。
 */
final class FileRoutingRuntimeBuilder {

    FileEditRuntimeComponents build(
            FileProjectWorkspace workspace,
            RuntimeWorkingSetResolver runtimeWorkingSetResolver,
            PatchRuntimeComponents patchRuntime,
            int maxFileGenerationAttempts
    ) {
        TaskPackageMarkdownRenderer taskPackageMarkdownRenderer = new TaskPackageMarkdownRenderer();
        FileScopedContextSupport fileScopedContextSupport = new FileScopedContextSupport(
                workspace,
                runtimeWorkingSetResolver,
                patchRuntime.patchContextBuilder(),
                taskPackageMarkdownRenderer
        );
        PatchRequestFactory patchRequestFactory = new PatchRequestFactory(fileScopedContextSupport);
        FilePatchRouteRequestFactory filePatchRouteRequestFactory = new FilePatchRouteRequestFactory(
                workspace,
                fileScopedContextSupport
        );
        FileGenerationFailureFactory fileGenerationFailureFactory = new FileGenerationFailureFactory();
        HtmlFilePatchExecutor htmlFilePatchExecutor = new HtmlFilePatchExecutor(
                patchRuntime.fileEditStrategyResolver(),
                patchRuntime.htmlEditRoutingPolicy(),
                patchRuntime.htmlFocusedRegionResolver(),
                patchRuntime.hostHtmlPatchExecutor(),
                patchRuntime.embeddedPatchExecutor(),
                patchRuntime.wholeFilePatchExecutor(),
                patchRequestFactory,
                fileGenerationFailureFactory,
                maxFileGenerationAttempts
        );
        CodeFilePatchExecutor codeFilePatchExecutor = new CodeFilePatchExecutor(
                patchRuntime.fileEditStrategyResolver(),
                patchRuntime.preciseCodePatchExecutor(),
                patchRuntime.wholeFilePatchExecutor(),
                patchRequestFactory,
                fileGenerationFailureFactory,
                maxFileGenerationAttempts
        );
        return new FileEditRuntimeComponents(
                patchRuntime.generatedContentGate(),
                patchRuntime.focusedHtmlRegionNormalizer(),
                patchRuntime.wholeFilePatchExecutor(),
                htmlFilePatchExecutor,
                codeFilePatchExecutor,
                fileScopedContextSupport,
                patchRequestFactory,
                filePatchRouteRequestFactory
        );
    }
}
