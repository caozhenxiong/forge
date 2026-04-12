package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 宿主嵌入 patch 执行所需的稳定输入。
 *
 * <p>脚本和样式这两条宿主内嵌路径都共享这批字段：
 * 1. 文件级上下文；
 * 2. 任务包 markdown；
 * 3. targeted context；
 * 4. 当前宿主内容与交付模式。
 *
 * <p>这样嵌入执行器不再依赖 `Subtask/ContractView/Fingerprint` 之类的上层对象。
 */
record EmbeddedTargetedRewriteRequest(
        Path projectPath,
        Path relativePath,
        String planSummary,
        String taskPackageMarkdown,
        String targetedContext,
        String feedback,
        String reason,
        String existingContent,
        String coderContextMarkdown,
        DeliveryMode deliveryMode,
        ImplementationEventJournal eventJournal,
        FileEditAttemptState editAttemptState,
        HtmlRuntimeOwnershipContract runtimeContract
) {
}
