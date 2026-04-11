package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 宿主 HTML patch 请求构造器。
 */
final class HostHtmlPatchRequestBuilder {

    private final PatchRequestContextFactory patchRequestContextFactory;

    HostHtmlPatchRequestBuilder(PatchRequestContextFactory patchRequestContextFactory) {
        this.patchRequestContextFactory = patchRequestContextFactory;
    }

    HostHtmlPatchRequest build(
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
            FilePatchProgressState patchProgressState,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        PatchRequestContext context = patchRequestContextFactory.hostHtml(taskPackage);
        return new HostHtmlPatchRequest(
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
                patchProgressState,
                runtimeContract
        );
    }
}
