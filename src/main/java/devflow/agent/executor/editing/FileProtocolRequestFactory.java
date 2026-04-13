package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一构造 patch 执行链的请求对象。
 *
 * <p>宿主 HTML、宿主嵌入、代码 patch、whole-file 例外路径都会消费同一批
 * 文件级上下文。把请求装配从协调器里拿出来后，协调器只保留路由和执行编排。
 */
public final class FileProtocolRequestFactory {

    private final HtmlTargetedRewriteRequestBuilder hostHtmlPatchRequestBuilder;
    private final FileProtocolRequestBuilder fileScopedPatchRequestBuilder;

    public FileProtocolRequestFactory(FileScopedContextSupport fileScopedContextSupport) {
        FileProtocolRequestContextFactory patchRequestContextFactory = new FileProtocolRequestContextFactory(fileScopedContextSupport);
        this.hostHtmlPatchRequestBuilder = new HtmlTargetedRewriteRequestBuilder(patchRequestContextFactory);
        this.fileScopedPatchRequestBuilder = new FileProtocolRequestBuilder(patchRequestContextFactory);
    }

    HtmlTargetedRewriteRequest hostHtml(
            FileEditRequest request
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
                request.editAttemptState(),
                request.runtimeContract()
        );
    }

    EmbeddedTargetedRewriteRequest embedded(
            FileEditRequest request
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
                request.editAttemptState(),
                request.runtimeContract()
        );
    }

    CodeTargetedRewriteRequest code(
            FileEditRequest request
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
                request.editAttemptState()
        );
    }

    FullRewriteRequest wholeFile(
            FileEditRequest request
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
                request.fileSnapshot(),
                request.executionState().deliveryMode(),
                request.contractView(),
                request.fingerprint(),
                request.eventJournal(),
                request.runtimeContract()
        );
    }
}
