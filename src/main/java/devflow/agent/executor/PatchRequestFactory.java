package devflow.agent.executor;

/**
 * 统一构造 patch 执行链的请求对象。
 *
 * <p>宿主 HTML、宿主嵌入、代码 patch、whole-file 例外路径都会消费同一批
 * 文件级上下文。把请求装配从协调器里拿出来后，协调器只保留路由和执行编排。
 */
final class PatchRequestFactory {

    private final HostHtmlPatchRequestBuilder hostHtmlPatchRequestBuilder;
    private final FileScopedPatchRequestBuilder fileScopedPatchRequestBuilder;

    PatchRequestFactory(FileScopedContextSupport fileScopedContextSupport) {
        PatchRequestContextFactory patchRequestContextFactory = new PatchRequestContextFactory(fileScopedContextSupport);
        this.hostHtmlPatchRequestBuilder = new HostHtmlPatchRequestBuilder(patchRequestContextFactory);
        this.fileScopedPatchRequestBuilder = new FileScopedPatchRequestBuilder(patchRequestContextFactory);
    }

    HostHtmlPatchRequest hostHtml(
            FilePatchRouteRequest request
    ) {
        return hostHtmlPatchRequestBuilder.build(
                request.projectPath(),
                request.relativePath(),
                request.planSummary(),
                request.taskPackage(),
                request.feedback(),
                request.reason(),
                request.coderContextMarkdown(),
                request.executionState().deliveryMode(),
                request.eventJournal(),
                request.existingContent(),
                request.patchProgressState(),
                request.runtimeContract()
        );
    }

    EmbeddedPatchRequest embedded(
            FilePatchRouteRequest request
    ) {
        return fileScopedPatchRequestBuilder.embedded(
                request.projectPath(),
                request.relativePath(),
                request.planSummary(),
                request.subtask(),
                request.taskPackage(),
                request.feedback(),
                request.reason(),
                request.existingContent(),
                request.coderContextMarkdown(),
                request.executionState().deliveryMode(),
                request.contractView(),
                request.fingerprint(),
                request.eventJournal(),
                request.patchProgressState(),
                request.runtimeContract()
        );
    }

    CodePatchRequest code(
            FilePatchRouteRequest request
    ) {
        return fileScopedPatchRequestBuilder.code(
                request.projectPath(),
                request.relativePath(),
                request.planSummary(),
                request.subtask(),
                request.taskPackage(),
                request.feedback(),
                request.substantiveFeedback(),
                request.reason(),
                request.existingContent(),
                request.coderContextMarkdown(),
                request.executionState().deliveryMode(),
                request.contractView(),
                request.fingerprint(),
                request.eventJournal(),
                request.patchProgressState()
        );
    }

    WholeFilePatchRequest wholeFile(
            FilePatchRouteRequest request
    ) {
        return fileScopedPatchRequestBuilder.wholeFile(
                request.projectPath(),
                request.relativePath(),
                request.planSummary(),
                request.subtask(),
                request.taskPackage(),
                request.feedback(),
                request.reason(),
                request.existingContent(),
                request.executionState().deliveryMode(),
                request.contractView(),
                request.fingerprint(),
                request.eventJournal(),
                request.runtimeContract()
        );
    }
}
