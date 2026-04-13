package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 宿主 HTML patch 请求构造器。
 */
final class HtmlTargetedRewriteRequestBuilder {

    private final FileProtocolRequestContextFactory patchRequestContextFactory;

    HtmlTargetedRewriteRequestBuilder(FileProtocolRequestContextFactory patchRequestContextFactory) {
        this.patchRequestContextFactory = patchRequestContextFactory;
    }

    HtmlTargetedRewriteRequest build(
            Path projectPath,
            Path relativePath,
            String planSummary,
            TaskPackage taskPackage,
            String feedback,
            String reason,
            String coderContextMarkdown,
            DeliveryMode deliveryMode,
            ImplementationEventJournal eventJournal,
            String existingContent,
            FileEditAttemptState editAttemptState,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        FileProtocolRequestContext context = patchRequestContextFactory.hostHtml(taskPackage);
        return new HtmlTargetedRewriteRequest(
                projectPath,
                relativePath,
                planSummary,
                context.taskPackageMarkdown(),
                coderContextMarkdown,
                reason,
                feedback,
                context.targetedContext(),
                existingContent,
                deliveryMode,
                eventJournal,
                editAttemptState,
                runtimeContract
        );
    }
}
