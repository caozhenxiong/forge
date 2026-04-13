package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.editing.FileStateSnapshot;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 文件级 patch 路由执行的统一输入。
 *
 * <p>协调器在完成文件级上下文收敛后，把稳定字段一次性装进这份请求，
 * 后续 HTML/代码/whole-file 例外路径都只消费它，不再重复传递上层对象。
 */
public record FileEditRequest(
        Path projectPath,
        Path relativePath,
        String planSummary,
        Subtask subtask,
        TaskPackage taskPackage,
        String feedback,
        boolean substantiveFeedback,
        String reason,
        String existingContent,
        FileStateSnapshot fileSnapshot,
        String coderContextMarkdown,
        SubtaskExecutionState executionState,
        FileEditAttemptState editAttemptState,
        ContractView contractView,
        ProjectFingerprint fingerprint,
        ImplementationEventJournal eventJournal,
        FileChange scopedChange,
        HtmlRuntimeOwnershipContract runtimeContract
) {
}
