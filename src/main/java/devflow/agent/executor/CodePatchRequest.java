package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 代码文件 patch 执行所需的稳定输入。
 *
 * <p>这层把 `precise-code` 主链需要的文件级上下文固定下来，
 * 这样代码 patch 执行器只关心单元循环、预算和 apply/verify，
 * 不再直接依赖上层的 `Subtask/ContractView/Fingerprint`。
 */
record CodePatchRequest(
        Path projectPath,
        Path relativePath,
        String planSummary,
        String taskPackageMarkdown,
        String targetedContext,
        String feedback,
        boolean substantiveFeedback,
        String reason,
        String existingContent,
        String coderContextMarkdown,
        DeliveryMode deliveryMode,
        ImplementationEventJournal eventJournal,
        FilePatchProgressState patchProgressState
) {
}
