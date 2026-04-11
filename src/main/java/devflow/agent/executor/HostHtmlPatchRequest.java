package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 宿主 HTML patch 执行所需的稳定输入。
 *
 * <p>这层把宿主 HTML 执行链共享的上下文收成一个对象，避免
 * `FileEditCoordinator` 继续把同一批参数在 precise-html /
 * focused-region / structured-draft 三条分支里重复传递。
 */
record HostHtmlPatchRequest(
        Path projectPath,
        Path relativePath,
        String planSummary,
        String taskPackageMarkdown,
        String coderContextMarkdown,
        String reason,
        String feedback,
        String targetedContext,
        String existingContent,
        DeliveryMode deliveryMode,
        ImplementationEventJournal eventJournal,
        FilePatchProgressState patchProgressState,
        HtmlRuntimeOwnershipContract runtimeContract
) {
}
