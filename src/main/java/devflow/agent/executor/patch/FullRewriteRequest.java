package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.precise.FileStateSnapshot;
import java.nio.file.Path;

import devflow.agent.executor.implementation.ImplementationEventJournal;
/**
 * 整文件重写主链的稳定输入。
 *
 * <p>whole-file 已经退到例外路径，但在允许使用的少数场景里，
 * 仍然需要一份固定请求对象承接文件级上下文，避免协调器继续内联大段 prompt 与执行参数。
 */
public record FullRewriteRequest(
        Path projectPath,
        Path relativePath,
        String planSummary,
        String taskPackageMarkdown,
        String targetedContext,
        String feedback,
        String reason,
        String existingContent,
        FileStateSnapshot fileSnapshot,
        DeliveryMode deliveryMode,
        ImplementationEventJournal eventJournal,
        HtmlRuntimeOwnershipContract runtimeContract
) {
}
