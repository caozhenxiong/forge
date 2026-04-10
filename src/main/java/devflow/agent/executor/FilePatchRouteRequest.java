package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

/**
 * 文件级 patch 路由执行的统一输入。
 *
 * <p>协调器在完成文件级上下文收敛后，把稳定字段一次性装进这份请求，
 * 后续 HTML/代码/whole-file 例外路径都只消费它，不再重复传递上层对象。
 */
record FilePatchRouteRequest(
        Path projectPath,
        Path relativePath,
        String planSummary,
        Subtask subtask,
        TaskPackage taskPackage,
        String feedback,
        boolean substantiveFeedback,
        String reason,
        String existingContent,
        String coderContextMarkdown,
        SubtaskExecutionState executionState,
        FilePatchProgressState patchProgressState,
        ContractView contractView,
        ProjectFingerprint fingerprint,
        ImplementationEventJournal eventJournal,
        FileChange scopedChange
) {
}
